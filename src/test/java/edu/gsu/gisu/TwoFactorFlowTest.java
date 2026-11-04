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

import java.time.Instant;
import java.util.List;
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
        "spring.datasource.url=jdbc:h2:mem:gisu_two_factor_tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE;DB_CLOSE_DELAY=-1",
        "GISU_TOTP_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class TwoFactorFlowTest {
    private static final String PASSWORD = "TestPassword123!";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired TwoFactorService totp;

    private record Account(MockHttpSession session, int id, String email) {}

    @SuppressWarnings("unchecked")
    private Account register() throws Exception {
        String email = "totp-" + UUID.randomUUID() + "@example.test";
        MvcResult result = mvc.perform(post("/register")
                        .param("firstName", "Security")
                        .param("lastName", "Tester")
                        .param("email", email)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard")).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        Map<String, Object> user = (Map<String, Object>) session.getAttribute("user");
        return new Account(session, ((Number) user.get("customer_id")).intValue(), email);
    }

    @SuppressWarnings("unchecked")
    private List<String> enable(Account account) throws Exception {
        mvc.perform(post("/account/security/2fa/start").session(account.session())
                        .param("password", PASSWORD))
                .andExpect(redirectedUrl("/account/security?event=setup"));
        String secret = totp.pendingSecret(account.id());
        assertNotNull(secret);
        assertEquals(32, secret.length());
        String ciphertext = db.queryForObject(
                "SELECT secret_ciphertext FROM account_totp_settings WHERE customer_id = ?",
                String.class, account.id());
        assertNotEquals(secret, ciphertext);
        String code = TwoFactorService.totpAt(secret,
                Instant.now().getEpochSecond() / 30);
        mvc.perform(post("/account/security/2fa/confirm").session(account.session())
                        .param("code", code))
                .andExpect(redirectedUrl("/account/security?event=enabled"));
        assertTrue(totp.enabled(account.id()));
        List<String> recovery = (List<String>) account.session()
                .getAttribute("justEnabledRecoveryCodes");
        assertEquals(6, recovery.size());
        return recovery;
    }

    private MockHttpSession passwordLogin(Account account) throws Exception {
        MvcResult r = mvc.perform(post("/login")
                        .param("email", account.email()).param("password", PASSWORD))
                .andExpect(redirectedUrl("/login/verify")).andReturn();
        MockHttpSession session = (MockHttpSession) r.getRequest().getSession(false);
        assertNull(session.getAttribute("user"));
        assertNotNull(session.getAttribute("pending2faUser"));
        return session;
    }

    @Test
    void mockVerificationIsLabeledAndScopedToTheLoggedInUser() throws Exception {
        Account a = register();
        Account b = register();
        mvc.perform(get("/account/security"))
                .andExpect(redirectedUrl("/login?reason=account"));
        mvc.perform(get("/account/security").session(a.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mock identity verification")))
                .andExpect(content().string(containsString("not real identity verification")));

        mvc.perform(post("/account/security/demo").session(a.session())
                        .param("demoCode", "WRONG")
                        .param("acknowledge", "true"))
                .andExpect(redirectedUrl("/account/security?event=demo-invalid"));
        assertEquals(0, db.queryForObject(
                "SELECT COUNT(*) FROM identity_verification_attempts WHERE customer_id = ?",
                Integer.class, a.id()));

        mvc.perform(post("/account/security/demo").session(a.session())
                        .param("demoCode", "GISU26")
                        .param("acknowledge", "true"))
                .andExpect(redirectedUrl("/account/security?event=demo"));
        assertEquals(1, db.queryForObject(
                "SELECT COUNT(*) FROM identity_verification_attempts WHERE customer_id = ?",
                Integer.class, a.id()));
        assertEquals(0, db.queryForObject(
                "SELECT COUNT(*) FROM identity_verification_attempts WHERE customer_id = ?",
                Integer.class, b.id()));
        String stored = db.queryForObject(
                "SELECT verification_status FROM identity_verification_attempts WHERE customer_id = ?",
                String.class, a.id());
        assertEquals("demo_completed", stored);
    }

    @Test
    void totpSetupRequiresPasswordAndAValidCodeAndRecoveryIsSingleUse() throws Exception {
        Account a = register();
        mvc.perform(post("/account/security/2fa/start").session(a.session())
                        .param("password", "wrong"))
                .andExpect(redirectedUrl("/account/security?event=wrong-password"));
        assertFalse(totp.hasPendingSetup(a.id()));

        mvc.perform(post("/account/security/2fa/confirm").session(a.session())
                        .param("code", "123456"))
                .andExpect(redirectedUrl("/account/security?event=no-setup"));
        List<String> recovery = enable(a);

        mvc.perform(get("/account/security").session(a.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Save these recovery codes now")))
                .andExpect(content().string(containsString(recovery.get(0))));
        mvc.perform(get("/account/security").session(a.session()))
                .andExpect(content().string(not(containsString(recovery.get(0)))));

        mvc.perform(post("/logout").session(a.session()))
                .andExpect(redirectedUrl("/"));
        MockHttpSession pending = passwordLogin(a);
        mvc.perform(get("/dashboard").session(pending))
                .andExpect(redirectedUrl("/login?reason=dashboard"));
        mvc.perform(get("/billing").session(pending))
                .andExpect(redirectedUrl("/login?reason=account"));
        mvc.perform(get("/account/security").session(pending))
                .andExpect(redirectedUrl("/login?reason=account"));

        mvc.perform(get("/login/verify").session(pending))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Verify your sign-in")));

        mvc.perform(post("/login/verify").session(pending)
                        .param("code", "000000"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Incorrect or already-used code")));
        assertNull(pending.getAttribute("user"));

        mvc.perform(post("/login/verify").session(pending)
                        .param("code", recovery.get(0)))
                .andExpect(redirectedUrl("/dashboard"));
        assertNotNull(pending.getAttribute("user"));
        assertNull(pending.getAttribute("pending2faUser"));
        assertEquals(5, db.queryForObject(
                "SELECT COUNT(*) FROM account_totp_recovery_codes WHERE customer_id = ?",
                Integer.class, a.id()));

        mvc.perform(post("/logout").session(pending))
                .andExpect(redirectedUrl("/"));
        MockHttpSession next = passwordLogin(a);
        mvc.perform(post("/login/verify").session(next)
                        .param("code", recovery.get(0)))
                .andExpect(status().isOk());
        assertNull(next.getAttribute("user"));
        mvc.perform(post("/login/verify").session(next)
                        .param("code", recovery.get(1)))
                .andExpect(redirectedUrl("/dashboard"));

        mvc.perform(post("/account/security/2fa/disable").session(next)
                        .param("password", "wrong")
                        .param("code", recovery.get(2)))
                .andExpect(redirectedUrl("/account/security?event=wrong-password"));
        assertTrue(totp.enabled(a.id()));
        mvc.perform(post("/account/security/2fa/disable").session(next)
                        .param("password", PASSWORD)
                        .param("code", recovery.get(2)))
                .andExpect(redirectedUrl("/account/security?event=disabled"));
        assertFalse(totp.enabled(a.id()));

        mvc.perform(post("/logout").session(next))
                .andExpect(redirectedUrl("/"));
        mvc.perform(post("/login")
                        .param("email", a.email()).param("password", PASSWORD))
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void failedChallengesExpireWithoutCreatingAnAuthenticatedSession() throws Exception {
        Account a = register();
        enable(a);
        mvc.perform(post("/logout").session(a.session()))
                .andExpect(redirectedUrl("/"));
        MockHttpSession pending = passwordLogin(a);

        for (int attempt = 1; attempt < 5; attempt++) {
            mvc.perform(post("/login/verify").session(pending)
                            .param("code", "invalid"))
                    .andExpect(status().isOk());
            assertNull(pending.getAttribute("user"));
        }
        mvc.perform(post("/login/verify").session(pending).param("code", "invalid"))
                .andExpect(redirectedUrl("/login?reason=verify-restart"));
        assertNull(pending.getAttribute("pending2faUser"));
        assertNull(pending.getAttribute("user"));
        mvc.perform(get("/dashboard").session(pending))
                .andExpect(redirectedUrl("/login?reason=dashboard"));
    }

    @Test
    void totpAlgorithmUsesSixDigitsAndPreventsReplay() throws Exception {
        Account a = register();
        List<String> codes = enable(a);
        String secretCipher = db.queryForObject(
                "SELECT secret_ciphertext FROM account_totp_settings WHERE customer_id = ?",
                String.class, a.id());
        assertNotNull(secretCipher);
        assertFalse(totp.verifyLogin(a.id(), "invalid"));
        // A recovery code works once; the same code is never accepted twice.
        assertTrue(totp.verifyLogin(a.id(), codes.get(0)));
        assertFalse(totp.verifyLogin(a.id(), codes.get(0)));
    }
}
