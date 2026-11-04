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
        "spring.datasource.url=jdbc:h2:mem:gisu_enrollment_redirect_tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE;DB_CLOSE_DELAY=-1"
})
class OfficialEnrollmentFlowTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    private MockHttpSession register() throws Exception {
        MvcResult r = mvc.perform(post("/register")
                .param("firstName", "Referral")
                .param("lastName", "Test")
                .param("email", "referral-" + UUID.randomUUID() + "@example.test")
                .param("password", "TestPassword123!"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    @Test
    void homepageIsConciseWithWorkingLinksAndNoMarketingTaglines() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Georgia health insurance plans")))
                .andExpect(content().string(containsString("Browse plans")))
                .andExpect(content().string(containsString("Take the assessment")))
                .andExpect(content().string(containsString("My dashboard")))
                .andExpect(content().string(not(containsString("Health coverage, made clearer"))))
                .andExpect(content().string(not(containsString("Three steps toward"))))
                .andExpect(content().string(not(containsString("Make your next coverage decision"))));
    }

    @Test
    void compactHomepageAndDropdownStylesAreActuallyServed() throws Exception {
        mvc.perform(get("/styles.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".home-compact .home-landing{")))
                .andExpect(content().string(containsString(".home-compact .home-quick-link{")))
                .andExpect(content().string(containsString(".site-header .nav-account-menu{")))
                .andExpect(content().string(containsString(".dashboard-quick-actions{")));

        MockHttpSession signedIn = register();
        mvc.perform(get("/dashboard").session(signedIn))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dashboard-quick-actions")))
                .andExpect(content().string(containsString("Agent help")))
                .andExpect(content().string(containsString("Reports")));

        mvc.perform(get("/").session(signedIn))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("nav-account-menu")))
                .andExpect(content().string(containsString("Agent help")));
    }

    @Test
    void officialPlanHandoffWorksWithoutAccountAndValidatesDestination() throws Exception {
        mvc.perform(get("/enrollment/plan/gisu-plan-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Continue to official enrollment")))
                .andExpect(content().string(containsString("SoloCare Gold PPO")))
                .andExpect(content().string(containsString("Select an official website to continue")));

        mvc.perform(post("/enrollment/referrals")
                        .param("planSlug", "gisu-plan-01")
                        .param("destination", "issuer"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://enroll.alliantplans.com/"));

        mvc.perform(post("/enrollment/referrals")
                        .param("planSlug", "gisu-plan-01")
                        .param("destination", "georgia-access"))
                .andExpect(redirectedUrl("https://georgiaaccess.gov/"));

        mvc.perform(post("/enrollment/referrals")
                        .param("planSlug", "gisu-plan-01")
                        .param("destination", "https://untrusted.example.com"))
                .andExpect(redirectedUrl("/plans?enrollment=invalid"));

        mvc.perform(post("/enrollment/referrals")
                        .param("planSlug", "not-a-catalog-plan")
                        .param("destination", "issuer"))
                .andExpect(redirectedUrl("/plans?enrollment=invalid"));

        mvc.perform(get("/enrollment/plan/not-a-catalog-plan"))
                .andExpect(redirectedUrl("/plans?enrollment=invalid"));

        assertEquals(0, db.queryForObject(
                "SELECT COUNT(*) FROM enrollment_referrals", Integer.class));
    }

    @Test
    void signedInReferralRecordsOnlyExternalClickAndStaysAccountPrivate() throws Exception {
        MockHttpSession accountA = register();
        MockHttpSession accountB = register();

        mvc.perform(post("/enrollment/referrals").session(accountA)
                        .param("planSlug", "gisu-plan-02")
                        .param("destination", "issuer"))
                .andExpect(redirectedUrl("https://enroll.alliantplans.com/"));

        mvc.perform(get("/dashboard").session(accountA))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Links you've followed")))
                .andExpect(content().string(containsString("SoloCare Silver PPO")))
                .andExpect(content().string(containsString("Insurer website")));

        mvc.perform(get("/dashboard").session(accountB))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No official enrollment links recorded yet")))
                .andExpect(content().string(not(containsString("SoloCare Silver PPO"))));

        assertEquals(1, db.queryForObject(
                "SELECT COUNT(*) FROM enrollment_referrals", Integer.class));
        assertEquals(0, db.queryForObject(
                "SELECT COUNT(*) FROM enrollments", Integer.class));
    }

    @Test
    void fictionalEnrollmentEndpointsAreDisabledAndLegacyFormsRemoved() throws Exception {
        MockHttpSession session = register();
        for (String path : new String[] {
                "/enroll", "/cancel-enrollment", "/request-extension", "/switch-plan"
        }) {
            mvc.perform(post(path).session(session))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Archived classroom enrollment data"))))
                .andExpect(content().string(not(containsString("action=\"/enroll\""))))
                .andExpect(content().string(not(containsString("action=\"/cancel-enrollment\""))))
                .andExpect(content().string(not(containsString("action=\"/switch-plan\""))));
    }

    @Test
    void siteUsesConciseCopyAndKeepsGeminiConsentVisible() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("educational prototype"))))
                .andExpect(content().string(containsString("Student project")));

        MockHttpSession signedIn = register();
        mvc.perform(get("/assessment").session(signedIn))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Important:"))))
                .andExpect(content().string(not(containsString("cannot yet confirm"))));

        mvc.perform(get("/agent-assistance").session(signedIn))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Classroom demonstration"))));
        mvc.perform(get("/reports").session(signedIn))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("This is not a claims portal"))));
        mvc.perform(get("/dashboard").session(signedIn))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Payroll demo"))))
                .andExpect(content().string(not(containsString("Archived classroom enrollment data"))));
        mvc.perform(get("/account/security").session(signedIn))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Practice code")))
                .andExpect(content().string(not(containsString("Demo only"))));
    }
}
