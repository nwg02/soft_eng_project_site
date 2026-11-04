package edu.gsu.gisu;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:gisu_account_chat_tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE;DB_CLOSE_DELAY=-1"
})
class AccountChatFlowTest {
    @Autowired MockMvc mvc;
    @Autowired AccountChatService accountChatService;

    private record Account(MockHttpSession session, int id) {}

    private Account register() throws Exception {
        MvcResult result = mvc.perform(post("/register")
                        .param("firstName", "Account")
                        .param("lastName", "ChatTest")
                        .param("email", "chat-" + UUID.randomUUID() + "@example.test")
                        .param("password", "TestPassword123!"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> principal = (Map<String, Object>) session.getAttribute("user");
        return new Account(session, ((Number) principal.get("customer_id")).intValue());
    }

    private MvcResult ask(MockHttpSession session, String question) throws Exception {
        var builder = post("/api/chat").contentType("application/json")
                .content("{\"message\":" + new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(question) + "}");
        if (session != null) builder.session(session);
        return mvc.perform(builder).andExpect(status().isOk()).andReturn();
    }

    @Test
    void userIntentIsNarrowAndDoesNotHijackGeneralInsuranceQuestions() {
        assertEquals(AccountChatService.Topic.NOTIFICATIONS,
                accountChatService.detectTopic("What are my notifications?"));
        assertEquals(AccountChatService.Topic.REPORTS,
                accountChatService.detectTopic("Summarize my reports"));
        assertEquals(AccountChatService.Topic.SAVED_PLANS,
                accountChatService.detectTopic("Compare my saved plans"));
        assertEquals(AccountChatService.Topic.ASSESSMENTS,
                accountChatService.detectTopic("What did my last assessment say?"));
        assertEquals(AccountChatService.Topic.AGENT_REQUESTS,
                accountChatService.detectTopic("Show my agent requests"));
        assertEquals(AccountChatService.Topic.OVERVIEW,
                accountChatService.detectTopic("Summarize my dashboard"));
        assertNull(accountChatService.detectTopic("What is a deductible?"));
        assertNull(accountChatService.detectTopic("What is a notification?"));
    }

    @Test
    void guestsGetNoAccountDataAndAccountsStayIsolated() throws Exception {
        Account a = register();
        Account b = register();

        mvc.perform(post("/reports").session(a.session)
                        .param("category", "network")
                        .param("description", "privateusernotealpha about provider network"))
                .andExpect(redirectedUrl("/reports?event=created"));

        MvcResult guest = ask(null, "What are my notifications?");
        assertEquals("account-login", new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(guest.getResponse().getContentAsString()).path("mode").asText());
        assertTrue(guest.getResponse().getContentAsString().contains("Please sign in"));
        assertFalse(guest.getResponse().getContentAsString().contains("Issue report saved"));

        MvcResult owner = ask(a.session, "What are my notifications?");
        assertTrue(owner.getResponse().getContentAsString().contains("Issue report saved"));
        assertTrue(owner.getResponse().getContentAsString().contains("\"mode\":\"account\""));

        MvcResult other = ask(b.session, "What are my notifications?");
        assertTrue(other.getResponse().getContentAsString().contains("0 GISU notifications"));
        assertFalse(other.getResponse().getContentAsString().contains("Issue report saved"));
        assertFalse(other.getResponse().getContentAsString().contains("privateusernotealpha"));
    }

    @Test
    void privateSummariesWorkAndGeminiSnapshotOmitsUserFreeTextAndDemographics()
            throws Exception {
        Account a = register();

        mvc.perform(post("/reports").session(a.session)
                        .param("category", "billing")
                        .param("description", "myprivatereportkeyword888 involves sample costs"))
                .andExpect(redirectedUrl("/reports?event=created"));
        mvc.perform(post("/agent-assistance").session(a.session)
                        .param("contactMethod", "email")
                        .param("preferredTime", "morning")
                        .param("topic", "compare")
                        .param("notes", "myprivateagentkeyword777"))
                .andExpect(redirectedUrl("/agent-assistance?event=recorded"));
        mvc.perform(post("/saved-plans").session(a.session)
                        .param("planSlug", "gisu-plan-01"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/assessment").session(a.session)
                        .param("age", "41")
                        .param("zipCode", "30303")
                        .param("coverageType", "individual")
                        .param("householdSize", "1")
                        .param("monthlyBudget", "9876.54")
                        .param("incomeRange", "under_30000")
                        .param("priority", "provider_flexibility"))
                .andExpect(status().is3xxRedirection());

        String reportsLocal = ask(a.session, "Summarize my reports")
                .getResponse().getContentAsString();
        assertTrue(reportsLocal.contains("myprivatereportkeyword888"));

        String savedLocal = ask(a.session, "Compare my saved plans")
                .getResponse().getContentAsString();
        assertTrue(savedLocal.contains("SoloCare Gold PPO"));
        assertTrue(savedLocal.contains("bookmarks, not coverage"));

        String assessmentLocal = ask(a.session, "Summarize my assessment")
                .getResponse().getContentAsString();
        assertTrue(assessmentLocal.contains("9876.54"));
        assertTrue(assessmentLocal.contains("30303"));

        String agentLocal = ask(a.session, "Show my agent requests")
                .getResponse().getContentAsString();
        assertTrue(agentLocal.contains("myprivateagentkeyword777"));

        String accountLocal = ask(a.session, "Summarize my dashboard")
                .getResponse().getContentAsString();
        assertTrue(accountLocal.contains("1 saved plan documents"));

        for (AccountChatService.Topic topic : AccountChatService.Topic.values()) {
            String outbound = accountChatService.summarize(a.id, topic).geminiSnapshot();
            assertFalse(outbound.contains("myprivatereportkeyword888"), topic.name());
            assertFalse(outbound.contains("myprivateagentkeyword777"), topic.name());
            assertFalse(outbound.contains("30303"), topic.name());
            assertFalse(outbound.contains("9876.54"), topic.name());
            assertFalse(outbound.contains("under_30000"), topic.name());
            assertFalse(outbound.contains("41"), topic.name());
        }

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("chat-account-consent"))));
        mvc.perform(get("/").session(a.session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("chat-account-consent")));
    }
}
