package edu.gsu.gisu;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/** Account security settings and an explicitly fictional identity-check demo. */
@Controller
public class AccountSecurityController {
    private static final String DEMO_CODE = "GISU26";
    private final JdbcTemplate db;
    private final TwoFactorService totp;

    public AccountSecurityController(JdbcTemplate db, TwoFactorService totp) {
        this.db = db;
        this.totp = totp;
    }

    @GetMapping("/account/security")
    public String security(HttpSession session, Model model) {
        Integer id = accountId(session);
        if (id == null) return "redirect:/login?reason=account";
        model.addAttribute("twoFactorEnabled", totp.enabled(id));
        boolean pending = totp.hasPendingSetup(id);
        model.addAttribute("twoFactorPending", pending);
        if (pending) {
            model.addAttribute("twoFactorSecret", totp.pendingSecret(id));
            model.addAttribute("twoFactorUri", totp.provisioningUri(id, accountEmail(session)));
        }
        List<String> recovery = recoveryCodes(session);
        model.addAttribute("recoveryCodes", recovery);
        model.addAttribute("demoVerificationHistory", db.queryForList(
                "SELECT verification_status, result_message, created_at " +
                "FROM identity_verification_attempts WHERE customer_id = ? " +
                "ORDER BY created_at DESC, verification_id DESC LIMIT 5", id));
        return "account-security";
    }

    @PostMapping("/account/security/demo")
    public String demo(@RequestParam(required = false) String demoCode,
                       @RequestParam(defaultValue = "false") boolean acknowledge,
                       HttpSession session) {
        Integer id = accountId(session);
        if (id == null) return "redirect:/login?reason=account";
        if (!acknowledge || demoCode == null ||
                !MessageDigest.isEqual(demoCode.trim().getBytes(StandardCharsets.US_ASCII),
                        DEMO_CODE.getBytes(StandardCharsets.US_ASCII))) {
            return "redirect:/account/security?event=demo-invalid";
        }
        db.update("INSERT INTO identity_verification_attempts " +
                "(customer_id, verification_method, verification_status, result_message) " +
                "VALUES (?, 'mock', 'demo_completed', " +
                "'Simulated classroom exercise only; identity was NOT verified.')", id);
        return "redirect:/account/security?event=demo";
    }

    @PostMapping("/account/security/2fa/start")
    public String start(@RequestParam(required = false) String password,
                        HttpSession session) {
        Integer id = accountId(session);
        if (id == null) return "redirect:/login?reason=account";
        if (!verifyPassword(id, password))
            return "redirect:/account/security?event=wrong-password";
        if (!totp.beginSetup(id)) return "redirect:/account/security?event=already-enabled";
        return "redirect:/account/security?event=setup";
    }

    @PostMapping("/account/security/2fa/confirm")
    public String confirm(@RequestParam(required = false) String code,
                          HttpSession session) {
        Integer id = accountId(session);
        if (id == null) return "redirect:/login?reason=account";
        if (!totp.hasPendingSetup(id)) return "redirect:/account/security?event=no-setup";
        List<String> recovery = totp.confirmSetup(id, code);
        if (recovery.isEmpty()) return "redirect:/account/security?event=wrong-code";
        session.setAttribute("justEnabledRecoveryCodes", recovery);
        return "redirect:/account/security?event=enabled";
    }

    @PostMapping("/account/security/2fa/disable")
    public String disable(@RequestParam(required = false) String password,
                          @RequestParam(required = false) String code,
                          HttpSession session) {
        Integer id = accountId(session);
        if (id == null) return "redirect:/login?reason=account";
        if (!verifyPassword(id, password))
            return "redirect:/account/security?event=wrong-password";
        if (!totp.disable(id, code)) return "redirect:/account/security?event=wrong-code";
        session.removeAttribute("justEnabledRecoveryCodes");
        return "redirect:/account/security?event=disabled";
    }

    private boolean verifyPassword(int id, String password) {
        if (password == null || password.isBlank()) return false;
        List<String> hashes = db.queryForList(
                "SELECT password_hash FROM customers WHERE customer_id = ?",
                String.class, id);
        return !hashes.isEmpty() && BCrypt.checkpw(password, hashes.get(0));
    }

    @SuppressWarnings("unchecked")
    private List<String> recoveryCodes(HttpSession session) {
        Object codes = session.getAttribute("justEnabledRecoveryCodes");
        session.removeAttribute("justEnabledRecoveryCodes");
        if (codes instanceof List<?> list &&
                list.stream().allMatch(x -> x instanceof String)) {
            return (List<String>) list;
        }
        return List.of();
    }

    private Integer accountId(HttpSession session) {
        Object principal = session.getAttribute("user");
        if (principal instanceof Map<?, ?> user &&
                user.get("customer_id") instanceof Number id) return id.intValue();
        return null;
    }

    private String accountEmail(HttpSession session) {
        Object principal = session.getAttribute("user");
        if (principal instanceof Map<?, ?> user &&
                user.get("email") instanceof String email) return email;
        return "account";
    }
}
