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

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gisu_assessment_tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE;DB_CLOSE_DELAY=-1"
})
class AssessmentFlowTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void catalogContainsVerifiedDocumentLinksAndPriorityOrdering() {
        List<GeorgiaPlanCatalog.Plan> plans = GeorgiaPlanCatalog.all();
        assertEquals(12, plans.size());
        assertEquals(12, plans.stream().map(GeorgiaPlanCatalog.Plan::documentUrl).distinct().count());
        assertTrue(plans.stream().allMatch(p -> p.documentUrl().startsWith("https://")));
        assertTrue(plans.stream().allMatch(p -> p.shopUrl().startsWith("https://")));
        assertEquals("Bronze", GeorgiaPlanCatalog.suggestions("lower_premium").get(0).metal());
        assertEquals("PPO", GeorgiaPlanCatalog.suggestions("provider_flexibility").get(0).type());
        assertEquals(500, GeorgiaPlanCatalog.suggestions("lower_deductible").get(0).deductibleInName());
        assertEquals(4, GeorgiaPlanCatalog.suggestions("balanced").size());
    }

    @Test
    void assessmentCanBeSavedRevisitedAndIsRestrictedToItsOwner() throws Exception {
        String email = "test-" + UUID.randomUUID() + "@example.test";

        MvcResult registration = mvc.perform(post("/register")
                        .param("firstName", "GISU")
                        .param("lastName", "Tester")
                        .param("email", email)
                        .param("password", "TestPassword123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) registration.getRequest().getSession(false);

        mvc.perform(get("/plans"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SoloCare Gold PPO")));

        MvcResult saved = mvc.perform(post("/assessment").session(session)
                        .param("age", "25")
                        .param("zipCode", "30303")
                        .param("coverageType", "individual")
                        .param("householdSize", "1")
                        .param("monthlyBudget", "325")
                        .param("incomeRange", "prefer_not")
                        .param("priority", "provider_flexibility"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String resultUrl = saved.getResponse().getRedirectedUrl();
        assertTrue(resultUrl != null && resultUrl.startsWith("/assessment/results/"));

        mvc.perform(get(resultUrl).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Four plans to compare further")))
                .andExpect(content().string(containsString("PPO examples appear first")));

        mvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Review results and official plan documents")));

        mvc.perform(get(resultUrl))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        MvcResult otherRegistration = mvc.perform(post("/register")
                        .param("firstName", "Different")
                        .param("lastName", "User")
                        .param("email", "other-" + UUID.randomUUID() + "@example.test")
                        .param("password", "TestPassword123!"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession otherSession = (MockHttpSession) otherRegistration.getRequest().getSession(false);
        mvc.perform(get(resultUrl).session(otherSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard?status=assessment_not_found"));

    }

    @Test
    void invalidGeorgiaZipDoesNotCreateAnAssessment() throws Exception {
        String email = "test-" + UUID.randomUUID() + "@example.test";
        MvcResult registration = mvc.perform(post("/register")
                        .param("firstName", "GISU")
                        .param("lastName", "Tester")
                        .param("email", email)
                        .param("password", "TestPassword123!"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) registration.getRequest().getSession(false);

        mvc.perform(post("/assessment").session(session)
                        .param("age", "25")
                        .param("zipCode", "90210")
                        .param("coverageType", "individual")
                        .param("householdSize", "1")
                        .param("monthlyBudget", "325")
                        .param("incomeRange", "prefer_not")
                        .param("priority", "balanced"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("five-digit Georgia ZIP")));
    }
}
