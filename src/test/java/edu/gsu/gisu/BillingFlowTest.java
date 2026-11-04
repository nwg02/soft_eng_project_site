package edu.gsu.gisu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gisu_billing_flow_tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE;DB_CLOSE_DELAY=-1"
})
class BillingFlowTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired BillingService billing;
    @Autowired AccountChatService accountChat;

    private record Account(MockHttpSession session, int id) {}

    @SuppressWarnings("unchecked")
    private Account register() throws Exception {
        MvcResult result = mvc.perform(post("/register")
                .param("firstName", "Billing")
                .param("lastName", "Tester")
                .param("email", "billing-" + UUID.randomUUID() + "@example.test")
                .param("password", "TestPassword123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        Map<String, Object> user = (Map<String, Object>) session.getAttribute("user");
        return new Account(session, ((Number) user.get("customer_id")).intValue());
    }

    private MvcResult postEntry(MockHttpSession session, String category, String amount,
                                String date, String status) throws Exception {
        return mvc.perform(post("/billing").session(session)
                .param("category", category).param("amount", amount)
                .param("entryDate", date).param("paymentStatus", status)).andReturn();
    }

    @Test
    void guestsMustLogInAndBillingPageIsAvailableFromTheAccount() throws Exception {
        mvc.perform(get("/billing"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?reason=account"));
        mvc.perform(post("/billing").param("amount", "100"))
                .andExpect(redirectedUrl("/login?reason=account"));

        Account a = register();
        mvc.perform(get("/billing").session(a.session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Billing &amp; payment history")))
                .andExpect(content().string(containsString("Track planned expenses and payments")))
                .andExpect(content().string(containsString("No planned payments yet")));

        mvc.perform(get("/dashboard").session(a.session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dashboard-billing-summary")));
        mvc.perform(get("/").session(a.session))
                .andExpect(content().string(containsString("Billing &amp; payments")));
    }

    @Test
    void userCanRecordAndReconcilePaymentsWithoutCreatingInsurerTransactions() throws Exception {
        Account a = register();
        Account b = register();
        String future = LocalDate.now().plusDays(7).toString();
        String today = LocalDate.now().toString();

        postEntry(a.session, "premium", "125.50", future, "planned");
        postEntry(a.session, "copay", "25.25", today, "recorded_paid");

        assertEquals("125.50", billing.overview(a.id).planned().toPlainString());
        assertEquals("25.25", billing.overview(a.id).paid().toPlainString());
        assertEquals(1, billing.overview(a.id).plannedCount());
        assertEquals(1, billing.overview(a.id).paidCount());

        mvc.perform(get("/billing").session(a.session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Insurance premium")))
                .andExpect(content().string(containsString("Copay or coinsurance")))
                .andExpect(content().string(containsString("$125.50")))
                .andExpect(content().string(containsString("$25.25")));

        mvc.perform(get("/billing").session(b.session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("$125.50"))))
                .andExpect(content().string(containsString("No planned payments yet")));

        Long plannedId = db.queryForObject(
                "SELECT payment_history_id FROM payment_history " +
                "WHERE customer_id = ? AND payment_status = 'planned'",
                Long.class, a.id);
        Long paidId = db.queryForObject(
                "SELECT payment_history_id FROM payment_history " +
                "WHERE customer_id = ? AND payment_status = 'recorded_paid'",
                Long.class, a.id);

        mvc.perform(post("/billing/" + plannedId + "/paid").session(b.session))
                .andExpect(redirectedUrl("/billing?event=unavailable#billing-history"));
        assertEquals(1, billing.overview(a.id).plannedCount());

        mvc.perform(post("/billing/" + plannedId + "/paid").session(a.session))
                .andExpect(redirectedUrl("/billing?event=paid#billing-history"));
        assertEquals(0, billing.overview(a.id).plannedCount());
        assertEquals("150.75", billing.overview(a.id).paid().toPlainString());

        mvc.perform(post("/billing/" + plannedId + "/paid").session(a.session))
                .andExpect(redirectedUrl("/billing?event=unavailable#billing-history"));

        mvc.perform(post("/billing/" + paidId + "/delete").session(b.session))
                .andExpect(redirectedUrl("/billing?event=unavailable#billing-history"));
        assertEquals(2, billing.overview(a.id).paidCount());

        mvc.perform(post("/billing/" + paidId + "/delete").session(a.session))
                .andExpect(redirectedUrl("/billing?event=deleted#billing-history"));
        assertEquals("125.50", billing.overview(a.id).paid().toPlainString());

        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM payments", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM enrollments", Integer.class));
        assertEquals(3, db.queryForObject(
                "SELECT COUNT(*) FROM user_notifications WHERE customer_id = ? " +
                "AND notification_type = 'billing'",
                Integer.class, a.id));
        assertEquals(0, db.queryForObject(
                "SELECT COUNT(*) FROM user_notifications WHERE customer_id = ? " +
                "AND notification_type = 'billing'", Integer.class, b.id));
    }

    @Test
    void invalidAmountsDatesCategoriesAndStatusesAreRejected() throws Exception {
        Account a = register();
        String today = LocalDate.now().toString();
        for (String amount : new String[]{"-4.00", "0", "1.234", "100000.01"}) {
            MvcResult r = postEntry(a.session, "premium", amount, today, "recorded_paid");
            assertEquals(200, r.getResponse().getStatus());
            assertTrue(r.getResponse().getContentAsString()
                    .contains("Enter a category, an amount"));
        }
        assertEquals(200, postEntry(a.session, "unknown", "14.00", today, "planned")
                .getResponse().getStatus());
        assertEquals(200, postEntry(a.session, "premium", "14.00",
                LocalDate.now().plusDays(5).toString(), "recorded_paid")
                .getResponse().getStatus());
        assertEquals(200, postEntry(a.session, "premium", "14.00", today, "invalid")
                .getResponse().getStatus());
        assertEquals(0, billing.overview(a.id).plannedCount());
        assertEquals(0, billing.overview(a.id).paidCount());
    }

    @Test
    void accountChatBillingIsReadOnlyAndGeminiSnapshotContainsNoFinancialDetails()
            throws Exception {
        Account a = register();
        postEntry(a.session, "premium", "482.91", LocalDate.now().toString(),
                "recorded_paid");
        assertEquals(AccountChatService.Topic.BILLING,
                accountChat.detectTopic("What is my billing history?"));
        assertEquals(AccountChatService.Topic.BILLING,
                accountChat.detectTopic("What payments have I made?"));
        assertNull(accountChat.detectTopic("What is a medical bill?"));

        AccountChatService.Summary summary = accountChat.summarize(
                a.id, AccountChatService.Topic.BILLING);
        assertTrue(summary.localAnswer().contains("$482.91"));
        assertFalse(summary.geminiSnapshot().contains("482.91"));
        assertFalse(summary.geminiSnapshot().contains("Insurance premium"));
        assertFalse(summary.geminiSnapshot().contains(LocalDate.now().toString()));

        String payload = new ObjectMapper().writeValueAsString(
                Map.of("message", "Summarize my payments"));
        MvcResult chat = mvc.perform(post("/api/chat").session(a.session)
                        .contentType("application/json").content(payload))
                .andExpect(status().isOk()).andReturn();
        JsonNode result = new ObjectMapper().readTree(chat.getResponse().getContentAsString());
        assertEquals("account", result.path("mode").asText());
        assertTrue(result.path("answer").asText().contains("$482.91"));
    }
}
