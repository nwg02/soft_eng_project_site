package edu.gsu.gisu;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:gisu_requirements_finish_tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE;DB_CLOSE_DELAY=-1"
})
class RequirementsFinishFlowTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired BillingService billing;
    @Autowired EnrollmentRequestService enrollment;
    @Autowired CoverageReminderService reminders;

    private record Account(MockHttpSession session, int customerId) {}

    @SuppressWarnings("unchecked")
    private Account register() throws Exception {
        MvcResult result = mvc.perform(post("/register")
                .param("firstName", "Feature").param("lastName", "Tester")
                .param("email", "feature-" + UUID.randomUUID() + "@example.test")
                .param("password", "TestPassword123!"))
                .andExpect(redirectedUrl("/dashboard")).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        var principal = (Map<String, Object>)session.getAttribute("user");
        return new Account(session, ((Number)principal.get("customer_id")).intValue());
    }

    @Test
    void payBillSimulationRecordsOnlyOwnedPlannedBill() throws Exception {
        Account a = register();
        Account b = register();
        LocalDate due = LocalDate.now().plusDays(3);
        mvc.perform(post("/billing").session(a.session)
                .param("category", "premium").param("paymentStatus", "planned")
                .param("amount", "175.25").param("entryDate", due.toString()))
                .andExpect(redirectedUrl("/billing?event=added#billing-history"));

        long billId = db.queryForObject(
                "SELECT payment_history_id FROM payment_history WHERE customer_id = ?",
                Long.class, a.customerId);
        mvc.perform(get("/billing").session(a.session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pay bill (sample)")));

        mvc.perform(post("/billing/" + billId + "/pay").session(b.session))
                .andExpect(redirectedUrl("/billing?event=unavailable#billing-history"));
        assertEquals(1, billing.overview(a.customerId).plannedCount());

        // Due-date notification appears once when the owner next visits dashboard.
        mvc.perform(get("/dashboard").session(a.session))
                .andExpect(status().isOk());
        mvc.perform(get("/dashboard").session(a.session))
                .andExpect(status().isOk());
        assertEquals(1, db.queryForObject(
                "SELECT COUNT(*) FROM user_notifications WHERE customer_id = ? " +
                "AND notification_type = 'payment_due'", Integer.class, a.customerId));
        assertEquals(0, db.queryForObject(
                "SELECT COUNT(*) FROM user_notifications WHERE customer_id = ? " +
                "AND notification_type = 'payment_due'", Integer.class, b.customerId));

        mvc.perform(post("/billing/" + billId + "/pay").session(a.session))
                .andExpect(redirectedUrl("/billing?event=payment#billing-history"));
        assertEquals(0, billing.overview(a.customerId).plannedCount());
        assertEquals(1, billing.overview(a.customerId).paidCount());
        assertTrue(billing.entries(a.customerId).get(0).samplePayment());

        mvc.perform(get("/billing").session(a.session))
                .andExpect(content().string(containsString("Sample payment")));
        mvc.perform(post("/billing/" + billId + "/pay").session(a.session))
                .andExpect(redirectedUrl("/billing?event=unavailable#billing-history"));
        assertEquals(1, billing.overview(a.customerId).paidCount());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM payments", Integer.class));
    }

    @Test
    void enrollmentRequestIsSavedLocallyAndNeverGrantsCoverage() throws Exception {
        Account a = register();
        Account b = register();
        String date = LocalDate.now().plusMonths(1).toString();
        mvc.perform(get("/enrollment/plan/gisu-plan-01").session(a.session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Save enrollment request")));
        mvc.perform(post("/enrollment/plan/gisu-plan-01/request")
                .param("desiredStart", date))
                .andExpect(redirectedUrl("/login?reason=account"));
        mvc.perform(post("/enrollment/plan/gisu-plan-01/request")
                .session(a.session).param("desiredStart", date))
                .andExpect(redirectedUrl("/enrollment/requests?event=saved"));
        assertEquals(1, enrollment.count(a.customerId));
        assertEquals(0, enrollment.count(b.customerId));
        assertEquals(1, db.queryForObject(
                "SELECT COUNT(*) FROM user_notifications WHERE customer_id = ? " +
                "AND notification_type = 'policy_update'", Integer.class, a.customerId));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM enrollments", Integer.class));
        mvc.perform(get("/enrollment/requests").session(a.session))
                .andExpect(content().string(containsString("SoloCare Gold PPO")))
                .andExpect(content().string(containsString("Ready to apply")));
        mvc.perform(get("/enrollment/requests").session(b.session))
                .andExpect(content().string(not(containsString("SoloCare Gold PPO"))));
        long requestId = db.queryForObject(
                "SELECT request_id FROM gisu_enrollment_requests WHERE customer_id = ?",
                Long.class, a.customerId);
        mvc.perform(post("/enrollment/requests/" + requestId + "/delete")
                .session(b.session))
                .andExpect(redirectedUrl("/enrollment/requests?event=invalid"));
        assertEquals(1, enrollment.count(a.customerId));
        mvc.perform(post("/enrollment/requests/" + requestId + "/delete")
                .session(a.session))
                .andExpect(redirectedUrl("/enrollment/requests?event=deleted"));
        assertEquals(0, enrollment.count(a.customerId));
    }

    @Test
    void renewalAlertsArePrivateOnceOnlyAndHaveRemovableSchedules() throws Exception {
        Account a = register();
        Account b = register();
        mvc.perform(get("/notifications").session(a.session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Policy renewal reminders")));
        mvc.perform(post("/notifications/renewals").session(a.session)
                .param("renewalDate", LocalDate.now().toString()))
                .andExpect(redirectedUrl("/notifications?event=reminder-saved"));
        mvc.perform(get("/notifications").session(a.session))
                .andExpect(content().string(containsString("Policy renewal reminder")));
        mvc.perform(get("/notifications").session(a.session))
                .andExpect(status().isOk());
        assertEquals(1, db.queryForObject(
                "SELECT COUNT(*) FROM user_notifications WHERE customer_id = ? " +
                "AND notification_type = 'policy_renewal'", Integer.class, a.customerId));
        assertEquals(0, db.queryForObject(
                "SELECT COUNT(*) FROM user_notifications WHERE customer_id = ? " +
                "AND notification_type = 'policy_renewal'", Integer.class, b.customerId));

        long reminderId = db.queryForObject(
                "SELECT reminder_id FROM gisu_renewal_reminders WHERE customer_id = ?",
                Long.class, a.customerId);
        mvc.perform(post("/notifications/renewals/" + reminderId + "/delete")
                .session(b.session))
                .andExpect(redirectedUrl("/notifications?event=unavailable"));
        assertEquals(1, reminders.renewals(a.customerId).size());
        mvc.perform(post("/notifications/renewals/" + reminderId + "/delete")
                .session(a.session))
                .andExpect(redirectedUrl("/notifications?event=reminder-deleted"));
        assertTrue(reminders.renewals(a.customerId).isEmpty());
    }
}
