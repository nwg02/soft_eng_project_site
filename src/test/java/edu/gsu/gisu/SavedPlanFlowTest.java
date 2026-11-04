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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gisu_saved_plans_tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE;DB_CLOSE_DELAY=-1"
})
class SavedPlanFlowTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    private MockHttpSession registerUser() throws Exception {
        MvcResult r = mvc.perform(post("/register")
                .param("firstName", "Bookmark")
                .param("lastName", "Tester")
                .param("email", "saved-" + UUID.randomUUID() + "@example.test")
                .param("password", "TestPassword123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    @Test
    void guestsCanBrowseButSavingRequiresLogin() throws Exception {
        mvc.perform(get("/plans"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in to save")));
        mvc.perform(post("/saved-plans").param("planSlug", "gisu-plan-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?reason=savedplan"));
        mvc.perform(get("/saved-plans"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?reason=savedplan"));
        mvc.perform(get("/login").param("reason", "savedplan"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in to save plans")));
    }

    @Test
    void savedDocumentsPersistAreUniqueAndArePrivateToTheirAccount() throws Exception {
        MockHttpSession a = registerUser();
        MockHttpSession b = registerUser();

        mvc.perform(post("/saved-plans").session(a)
                        .param("planSlug", "gisu-plan-01")
                        .param("returnTo", "plans"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/plans?bookmark=saved#gisu-plan-01"));

        mvc.perform(get("/plans").session(a))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Saved · Remove")));
        mvc.perform(get("/dashboard").session(a))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Saved insurance plan documents")))
                .andExpect(content().string(containsString("SoloCare Gold PPO $1500 $10 Chiro")));

        mvc.perform(get("/dashboard").session(b))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No saved plans yet")));

        // Idempotent save: no duplicate records for the same user and slug.
        mvc.perform(post("/saved-plans").session(a).param("planSlug", "gisu-plan-01"))
                .andExpect(status().is3xxRedirection());
        Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM saved_curated_plans WHERE plan_slug = ?",
                Integer.class, "gisu-plan-01");
        assertEquals(1, count);

        // Other account cannot remove a saved record owned by account A.
        mvc.perform(post("/saved-plans/remove").session(b)
                        .param("planSlug", "gisu-plan-01")
                        .param("returnTo", "dashboard"))
                .andExpect(redirectedUrl("/dashboard?bookmark=removed#saved-plans"));
        assertEquals(1, db.queryForObject(
                "SELECT COUNT(*) FROM saved_curated_plans WHERE plan_slug = ?",
                Integer.class, "gisu-plan-01"));

        mvc.perform(post("/saved-plans").session(a).param("planSlug", "not-real"))
                .andExpect(redirectedUrl("/plans?bookmark=invalid"));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM saved_curated_plans",
                Integer.class));

        mvc.perform(post("/saved-plans/remove").session(a)
                        .param("planSlug", "gisu-plan-01")
                        .param("returnTo", "dashboard"))
                .andExpect(redirectedUrl("/dashboard?bookmark=removed#saved-plans"));
        mvc.perform(get("/dashboard").session(a))
                .andExpect(content().string(containsString("No saved plans yet")));
    }
}
