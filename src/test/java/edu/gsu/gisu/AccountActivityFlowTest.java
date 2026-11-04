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

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gisu_account_activity_tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE;DB_CLOSE_DELAY=-1"
})
class AccountActivityFlowTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    private record TestAccount(MockHttpSession session, int id) {}

    private TestAccount register() throws Exception {
        MvcResult r = mvc.perform(post("/register")
                        .param("firstName", "Activity")
                        .param("lastName", "Tester")
                        .param("email", "activity-" + UUID.randomUUID() + "@example.test")
                        .param("password", "TestPassword123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) r.getRequest().getSession(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) session.getAttribute("user");
        return new TestAccount(session, ((Number) user.get("customer_id")).intValue());
    }

    private int count(String table, int customerId) {
        // Table names are internal constants only.
        if (!java.util.Set.of("agent_requests", "insurance_claim_records",
                             "user_notifications").contains(table)) {
            throw new IllegalArgumentException("Unknown table");
        }
        return db.queryForObject("SELECT COUNT(*) FROM " + table +
                " WHERE customer_id = ?", Integer.class, customerId);
    }

    @Test
    void accountToolsRequireLogin() throws Exception {
        for (String path : new String[]{"/agent-assistance", "/reports", "/notifications"}) {
            mvc.perform(get(path))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?reason=account"));
        }
        mvc.perform(post("/agent-assistance").param("topic", "compare"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?reason=account"));
        mvc.perform(post("/reports").param("category", "coverage"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?reason=account"));
    }

    @Test
    void demoAssistanceRequestCreatesOnlyPrivateRecordAndNotification() throws Exception {
        TestAccount a = register();
        TestAccount b = register();

        mvc.perform(post("/agent-assistance").session(a.session())
                        .param("contactMethod", "email")
                        .param("preferredTime", "morning")
                        .param("topic", "compare")
                        .param("notes", "Research HMO vs PPO plans"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/agent-assistance?event=recorded"));

        assertEquals(1, count("agent_requests", a.id()));
        assertEquals(1, count("user_notifications", a.id()));
        assertEquals(0, count("agent_requests", b.id()));

        mvc.perform(get("/agent-assistance").session(a.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Research HMO vs PPO plans")))
                .andExpect(content().string(containsString("Request saved")));

        mvc.perform(get("/agent-assistance").session(b.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Research HMO vs PPO plans"))));

        mvc.perform(post("/agent-assistance").session(a.session())
                        .param("contactMethod", "invalid")
                        .param("preferredTime", "morning")
                        .param("topic", "compare")
                        .param("notes", "Not saved"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Select a contact preference")));
        assertEquals(1, count("agent_requests", a.id()));
    }

    @Test
    void issueReportsTrackUserOnlyStatusesAndGenerateNotifications() throws Exception {
        TestAccount a = register();
        TestAccount b = register();
        String note = "Compare how the sample plans define out-of-network visits.";

        mvc.perform(post("/reports").session(a.session())
                        .param("category", "network")
                        .param("description", note))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reports?event=created"));
        assertEquals(1, count("insurance_claim_records", a.id()));
        assertEquals(1, count("user_notifications", a.id()));

        Long reportId = db.queryForObject(
                "SELECT claim_record_id FROM insurance_claim_records WHERE customer_id = ?",
                Long.class, a.id());
        String reportPath = "/reports/" + reportId;

        mvc.perform(get("/reports").session(a.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(note)))
                .andExpect(content().string(containsString("Open in GISU")));

        mvc.perform(get("/reports").session(b.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(note))));

        mvc.perform(post(reportPath + "/close").session(b.session()))
                .andExpect(redirectedUrl("/reports?event=unavailable"));
        assertEquals("open", db.queryForObject(
                "SELECT claim_status FROM insurance_claim_records WHERE claim_record_id = ?",
                String.class, reportId));

        mvc.perform(post(reportPath + "/close").session(a.session()))
                .andExpect(redirectedUrl("/reports?event=closed"));
        assertEquals("closed", db.queryForObject(
                "SELECT claim_status FROM insurance_claim_records WHERE claim_record_id = ?",
                String.class, reportId));

        mvc.perform(post(reportPath + "/reopen").session(a.session()))
                .andExpect(redirectedUrl("/reports?event=reopened"));
        assertEquals("open", db.queryForObject(
                "SELECT claim_status FROM insurance_claim_records WHERE claim_record_id = ?",
                String.class, reportId));
        assertEquals(3, count("user_notifications", a.id()));

        mvc.perform(post("/reports").session(a.session())
                        .param("category", "invalid")
                        .param("description", "This cannot be stored"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Choose a report category")));
        assertEquals(1, count("insurance_claim_records", a.id()));
    }

    @Test
    void notificationsArePrivateAndReadActionsAreAccountScoped() throws Exception {
        TestAccount a = register();
        TestAccount b = register();
        mvc.perform(post("/reports").session(a.session())
                        .param("category", "billing")
                        .param("description", "Compare two premium examples."))
                .andExpect(redirectedUrl("/reports?event=created"));

        Long nId = db.queryForObject(
                "SELECT notification_id FROM user_notifications WHERE customer_id = ?",
                Long.class, a.id());

        mvc.perform(get("/notifications").session(a.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Issue report saved")))
                .andExpect(content().string(containsString("Unread")));
        mvc.perform(get("/dashboard").session(a.session()))
                .andExpect(content().string(containsString("Unread notifications")));

        mvc.perform(get("/notifications").session(b.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Issue report saved"))));

        mvc.perform(post("/notifications/" + nId + "/read").session(b.session()))
                .andExpect(redirectedUrl("/notifications?event=unavailable"));
        assertEquals(1, db.queryForObject(
                "SELECT COUNT(*) FROM user_notifications WHERE notification_id = ? AND read_at IS NULL",
                Integer.class, nId));

        mvc.perform(post("/notifications/" + nId + "/read").session(a.session()))
                .andExpect(redirectedUrl("/notifications?event=read"));
        assertEquals(0, db.queryForObject(
                "SELECT COUNT(*) FROM user_notifications WHERE notification_id = ? AND read_at IS NULL",
                Integer.class, nId));

        mvc.perform(post("/notifications/read-all").session(a.session()))
                .andExpect(redirectedUrl("/notifications?event=all-read"));
    }
}
