package edu.gsu.gisu;

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

import java.io.IOException;
import java.math.BigDecimal;
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
        "spring.datasource.url=jdbc:h2:mem:gisu_ai_assessment_tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE;DB_CLOSE_DELAY=-1",
        "GEMINI_API_KEY="
})
class AiAssessmentFlowTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper mapper;
    @Autowired AiAssessmentService ai;

    private record Account(MockHttpSession session, int id) {}

    @SuppressWarnings("unchecked")
    private Account register() throws Exception {
        MvcResult r = mvc.perform(post("/register")
                .param("firstName", "AI")
                .param("lastName", "Assessment")
                .param("email", UUID.randomUUID() + "@example.test")
                .param("password", "TestPassword123!"))
                .andExpect(redirectedUrl("/dashboard")).andReturn();
        MockHttpSession session = (MockHttpSession) r.getRequest().getSession(false);
        Map<String, Object> p = (Map<String, Object>) session.getAttribute("user");
        return new Account(session, ((Number)p.get("customer_id")).intValue());
    }

    private long saveAssessment(Account owner) throws Exception {
        MvcResult r = mvc.perform(post("/assessment").session(owner.session())
                        .param("age", "25")
                        .param("zipCode", "30303")
                        .param("coverageType", "individual")
                        .param("householdSize", "1")
                        .param("monthlyBudget", "325.00")
                        .param("incomeRange", "under_30000")
                        .param("priority", "balanced"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String url = r.getResponse().getRedirectedUrl();
        assertNotNull(url);
        return Long.parseLong(url.substring(url.lastIndexOf('/')+1));
    }

    @Test
    void aiPromptIsRedactedAndOnlyCuratedPlansCanBeSelected() throws Exception {
        String prompt = AiAssessmentService.buildPrompt(
                "balanced", "individual", 1, new BigDecimal("325.00"));
        assertTrue(prompt.contains("$200 to $500"));
        assertTrue(prompt.contains("gisu-plan-01"));
        assertTrue(prompt.contains("gisu-plan-12"));
        assertFalse(prompt.contains("30303"));
        assertFalse(prompt.contains("under_30000"));
        assertFalse(prompt.contains("325.00"));
        assertFalse(prompt.contains("age:"));

        String summary = "This is a research shortlist, not a personalized eligibility or " +
                "pricing decision. Compare actual costs, plan documents and provider networks " +
                "with official insurers before applying.";
        String valid = mapper.writeValueAsString(Map.of(
                "summary", summary,
                "planSlugs", new String[]{"gisu-plan-01", "gisu-plan-05"}));
        AiAssessmentService.Generated parsed = AiAssessmentService.parseGenerated(mapper,valid);
        assertEquals(2, parsed.slugs().size());
        assertEquals("gisu-plan-01", parsed.slugs().get(0));
        assertThrows(IOException.class, () -> AiAssessmentService.parseGenerated(
                mapper, mapper.writeValueAsString(Map.of(
                        "summary", summary,
                        "planSlugs", new String[]{"not-a-plan", "gisu-plan-05"}))));
        assertThrows(IOException.class, () -> AiAssessmentService.parseGenerated(
                mapper, mapper.writeValueAsString(Map.of(
                        "summary", summary,
                        "planSlugs", new String[]{"gisu-plan-01", "gisu-plan-01"}))));
    }

    @Test
    void generationRequiresExplicitConsentAndNeverDisclosesAnotherAccount() throws Exception {
        Account a = register();
        Account b = register();
        long id = saveAssessment(a);
        mvc.perform(get("/assessment/results/" + id).session(a.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AI assessment")))
                .andExpect(content().string(containsString("Get an AI analysis of your coverage priorities")))
                .andExpect(content().string(containsString("Four plans to compare further")));

        mvc.perform(post("/assessment/results/" + id + "/ai").session(a.session()))
                .andExpect(redirectedUrl("/assessment/results/" + id + "?ai=consent-required"));
        mvc.perform(post("/assessment/results/" + id + "/ai").session(a.session())
                .param("shareRedactedPreferences", "true"))
                .andExpect(redirectedUrl("/assessment/results/" + id + "?ai=not-configured"));
        assertEquals(0, db.queryForObject(
                "SELECT COUNT(*) FROM assessment_ai_reviews", Integer.class));

        mvc.perform(post("/assessment/results/" + id + "/ai").session(b.session())
                .param("shareRedactedPreferences", "true"))
                .andExpect(redirectedUrl("/dashboard?status=assessment_not_found"));

        String summary = "This is a Gemini educational comparison for the chosen " +
                "priority. Check the monthly rates, local doctor network, and " +
                "other real plan documents with the insurer before any application.";
        db.update("INSERT INTO assessment_ai_reviews " +
                "(assessment_id, customer_id, summary_text, plan_slugs, model_name) " +
                "VALUES (?, ?, ?, ?, ?)",
                id, a.id(), summary, "gisu-plan-04,gisu-plan-05", "test-model");

        mvc.perform(get("/assessment/results/" + id).session(a.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AI-selected documents to compare")))
                .andExpect(content().string(containsString(summary)))
                .andExpect(content().string(containsString("Document 1 of 2")));

        mvc.perform(get("/assessment/results/" + id).session(b.session()))
                .andExpect(redirectedUrl("/dashboard?status=assessment_not_found"));
        assertNull(ai.storedReview(b.id(), id));
        assertEquals(2, ai.storedReview(a.id(), id).plans().size());
        assertEquals(AiAssessmentService.Result.EXISTING, ai.generate(a.id(), id));
    }
}
