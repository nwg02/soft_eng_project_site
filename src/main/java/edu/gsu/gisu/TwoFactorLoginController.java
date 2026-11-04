package edu.gsu.gisu;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/** Password-authenticated sessions must complete TOTP or a recovery code first. */
@Controller
public class TwoFactorLoginController {
    private static final int MAX_ATTEMPTS = 5;
    private final TwoFactorService totp;

    public TwoFactorLoginController(TwoFactorService totp) {
        this.totp = totp;
    }

    @GetMapping("/login/verify")
    public String verification(HttpSession session, Model model) {
        if (pendingCustomerId(session) == null) return "redirect:/login";
        int used = attempts(session);
        model.addAttribute("attemptsRemaining", Math.max(0, MAX_ATTEMPTS - used));
        return "login-verify";
    }

    @PostMapping("/login/verify")
    public String verify(@RequestParam(required = false) String code,
                         HttpSession session, Model model) {
        Integer id = pendingCustomerId(session);
        if (id == null) return "redirect:/login";
        int failures = attempts(session);
        if (failures >= MAX_ATTEMPTS) return clearPending(session);
        if (code == null || code.length() > 30 ||
                !totp.verifyLogin(id, code.trim())) {
            failures++;
            session.setAttribute("pending2faAttempts", failures);
            if (failures >= MAX_ATTEMPTS) return clearPending(session);
            model.addAttribute("error", "Incorrect or already-used code. " +
                    "Try the current authenticator code or an unused recovery code.");
            model.addAttribute("attemptsRemaining", MAX_ATTEMPTS - failures);
            return "login-verify";
        }
        Object principal = session.getAttribute("pending2faUser");
        if (!(principal instanceof Map<?, ?>)) return clearPending(session);
        session.changeSessionId();
        session.removeAttribute("pending2faUser");
        session.removeAttribute("pending2faAttempts");
        session.setAttribute("user", principal);
        return "redirect:/dashboard";
    }

    @PostMapping("/login/verify/cancel")
    public String cancel(HttpSession session) {
        return clearPending(session);
    }

    private String clearPending(HttpSession session) {
        session.removeAttribute("pending2faUser");
        session.removeAttribute("pending2faAttempts");
        return "redirect:/login?reason=verify-restart";
    }

    private int attempts(HttpSession session) {
        Object value = session.getAttribute("pending2faAttempts");
        return value instanceof Number count ? count.intValue() : 0;
    }

    private Integer pendingCustomerId(HttpSession session) {
        Object principal = session.getAttribute("pending2faUser");
        if (principal instanceof Map<?, ?> user &&
                user.get("customer_id") instanceof Number number) return number.intValue();
        return null;
    }
}
