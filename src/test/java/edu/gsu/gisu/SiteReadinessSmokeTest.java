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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("local")
@TestPropertySource(properties={
        "spring.datasource.url=jdbc:h2:mem:gisu_ui_readiness;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE;DB_CLOSE_DELAY=-1"
})
class SiteReadinessSmokeTest {
    @Autowired MockMvc mvc;

    @Test
    void publicRoutesAndAssetsRender() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("home-landing")))
                .andExpect(content().string(containsString("Georgia health insurance plans")));
        mvc.perform(get("/plans")).andExpect(status().isOk())
                .andExpect(content().string(containsString("market-grid")))
                .andExpect(content().string(containsString("Compare plans")));
        mvc.perform(get("/enrollment/plan/gisu-plan-01")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Continue to insurer")));
        mvc.perform(get("/register")).andExpect(status().isOk())
                .andExpect(content().string(containsString("auth-card")))
                .andExpect(content().string(containsString("autocomplete=\"new-password\"")))
                .andExpect(content().string(containsString("Already have an account?")));
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("auth-card")));
        mvc.perform(get("/api/chat/status")).andExpect(status().isOk());
        mvc.perform(get("/styles.css")).andExpect(status().isOk())
                .andExpect(content().string(containsString(".home-compact .home-landing")))
                .andExpect(content().string(containsString("@media(max-width:650px)")))
                .andExpect(content().string(containsString("prefers-reduced-motion")))
                .andExpect(content().string(containsString("focus-visible")));
        mvc.perform(get("/plan-comparison.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("buildComparison")));
        mvc.perform(get("/chat-widget.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("askQuestion")));
    }

    @Test
    void privatePagesRequireSignInAndRenderAfterLogin() throws Exception {
        for (String route : new String[]{
                "/dashboard","/billing","/notifications","/agent-assistance",
                "/reports","/account/security","/enrollment/requests","/payroll"
        }) {
            mvc.perform(get(route)).andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", containsString("/login")));
        }

        String email="smoke-"+UUID.randomUUID()+"@example.test";
        MvcResult result=mvc.perform(post("/register")
                .param("firstName","Screen").param("lastName","Tester")
                .param("email",email).param("password","TestPassword123!"))
                .andExpect(redirectedUrl("/dashboard")).andReturn();
        MockHttpSession session=(MockHttpSession)result.getRequest().getSession(false);

        for(String route:new String[]{
                "/dashboard","/assessment","/billing","/notifications",
                "/agent-assistance","/reports","/account/security",
                "/enrollment/requests","/enrollment/plan/gisu-plan-01","/payroll"
        }) {
            mvc.perform(get(route).session(session)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("site-header")))
                    .andExpect(content().string(containsString("site-footer")));
        }

        mvc.perform(get("/saved-plans").session(session))
                .andExpect(redirectedUrl("/dashboard#saved-plans"));
        mvc.perform(get("/dashboard").session(session))
                .andExpect(content().string(containsString("dashboard-quick-actions")))
                .andExpect(content().string(containsString("Security &amp; 2FA")))
                .andExpect(content().string(not(containsString("Payroll demo"))));
        mvc.perform(get("/billing").session(session))
                .andExpect(content().string(containsString("Payment history")));
        mvc.perform(get("/notifications").session(session))
                .andExpect(content().string(containsString("renewal-reminder-card")));

        mvc.perform(post("/logout").session(session)).andExpect(redirectedUrl("/"));
        mvc.perform(get("/dashboard"))
                .andExpect(redirectedUrl("/login?reason=dashboard"));
    }
}
