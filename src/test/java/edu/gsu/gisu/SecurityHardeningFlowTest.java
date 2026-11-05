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

import java.util.UUID;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gisu_csrf_security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE;DB_CLOSE_DELAY=-1"
})
class SecurityHardeningFlowTest {
    @Autowired MockMvc mvc;

    @Test
    void csrfProtectsAccountFormsAndChatWhileAllowingLegitimateRequests() throws Exception {
        mvc.perform(get("/register")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"chat-csrf\"")));
        mvc.perform(post("/register")
                .param("firstName", "Test").param("lastName", "User")
                .param("email", UUID.randomUUID() + "@example.test")
                .param("password", "TestPassword123!"))
                .andExpect(status().isForbidden());

        MvcResult registered = mvc.perform(post("/register").with(csrf())
                .param("firstName", "Security").param("lastName", "Tester")
                .param("email", UUID.randomUUID() + "@example.test")
                .param("password", "TestPassword123!"))
                .andExpect(redirectedUrl("/dashboard")).andReturn();
        MockHttpSession session = (MockHttpSession)registered.getRequest().getSession(false);
        assertNotNull(session);

        mvc.perform(post("/billing").session(session)
                .param("category", "premium").param("paymentStatus", "planned")
                .param("amount", "35.00").param("entryDate", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/billing").session(session).with(csrf())
                .param("category", "premium").param("paymentStatus", "planned")
                .param("amount", "35.00").param("entryDate", LocalDate.now().plusDays(1).toString()))
                .andExpect(redirectedUrl("/billing?event=added#billing-history"));

        mvc.perform(post("/api/chat").session(session)
                .contentType("application/json").content("{\"message\":\"What is a deductible?\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/chat").session(session).with(csrf())
                .contentType("application/json").content("{\"message\":\"What is a deductible?\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/logout").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void repeatedBadLoginsAreRateLimited() throws Exception {
        String email = "locked-" + UUID.randomUUID() + "@example.test";
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/login").with(csrf())
                    .param("email", email).param("password", "incorrect-password"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Email or password is incorrect")));
        }
        mvc.perform(post("/login").with(csrf())
                .param("email", email).param("password", "incorrect-password"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void csrfAndRateLimitDoNotPreventPublicBrowsing() throws Exception {
        mvc.perform(get("/plans")).andExpect(status().isOk());
        mvc.perform(get("/api/chat/status")).andExpect(status().isOk());
        mvc.perform(get("/enrollment/plan/gisu-plan-01")).andExpect(status().isOk());
    }
}
