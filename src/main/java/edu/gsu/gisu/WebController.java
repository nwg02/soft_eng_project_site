
package edu.gsu.gisu;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
public class WebController {
    private final JdbcTemplate db;
    private final SavedPlanService savedPlanService;
    private final AccountActivityService accountActivityService;
    private final EnrollmentReferralService enrollmentReferralService;
    private final BillingService billingService;
    private final TwoFactorService twoFactorService;

    public WebController(JdbcTemplate db, SavedPlanService savedPlanService,
                         AccountActivityService accountActivityService,
                         EnrollmentReferralService enrollmentReferralService,
                         BillingService billingService, TwoFactorService twoFactorService) {
        this.db = db;
        this.savedPlanService = savedPlanService;
        this.accountActivityService = accountActivityService;
        this.enrollmentReferralService = enrollmentReferralService;
        this.billingService = billingService;
        this.twoFactorService = twoFactorService;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String firstName,
                           @RequestParam String lastName,
                           @RequestParam String email,
                           @RequestParam String password,
                           Model model,
                           HttpSession session, HttpServletRequest servletRequest) {
        if (password.length() < 8) {
            model.addAttribute("error",
                    "Password must be at least 8 characters.");
            return "register";
        }

        try {
            String normalizedEmail = email.trim().toLowerCase();
            String hash = BCrypt.hashpw(password, BCrypt.gensalt());

            db.update(
                    "INSERT INTO customers " +
                    "(first_name, last_name, email, password_hash) " +
                    "VALUES (?, ?, ?, ?)",
                    firstName.trim(), lastName.trim(),
                    normalizedEmail, hash
            );

            Map<String, Object> user = db.queryForMap(
                    "SELECT customer_id, first_name, last_name, email, role " +
                    "FROM customers WHERE email = ?",
                    normalizedEmail
            );

            servletRequest.changeSessionId();
            session.setAttribute("user", user);
            return "redirect:/dashboard";
        } catch (DuplicateKeyException ex) {
            model.addAttribute("error",
                    "An account with that email already exists.");
            return "register";
        } catch (DataAccessException ex) {
            model.addAttribute("error",
                    "Account creation is temporarily unavailable because the database could not be accessed. If running locally, start the app with the local profile.");
            return "register";
        }
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String reason,
                            Model model) {
        model.addAttribute("dashboardLoginRequired", "dashboard".equals(reason));
        model.addAttribute("savedPlanLoginRequired", "savedplan".equals(reason));
        model.addAttribute("accountLoginRequired", "account".equals(reason));
        model.addAttribute("verifyRestartRequired", "verify-restart".equals(reason));
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        Model model,
                        HttpSession session, HttpServletRequest servletRequest) {
        List<Map<String, Object>> users;
        try {
            users = db.queryForList(
                    "SELECT customer_id, first_name, last_name, email, role, " +
                    "password_hash FROM customers WHERE email = ?",
                    email.trim().toLowerCase()
            );
        } catch (DataAccessException ex) {
            model.addAttribute("error",
                    "Sign-in is temporarily unavailable because the database could not be accessed. If running locally, start the app with the local profile.");
            return "login";
        }

        if (users.isEmpty() ||
                !BCrypt.checkpw(password,
                        (String) users.get(0).get("password_hash"))) {
            model.addAttribute("error",
                    "Email or password is incorrect.");
            return "login";
        }

        Map<String, Object> user = users.get(0);
        user.remove("password_hash");
        servletRequest.changeSessionId();
        session.removeAttribute("user");
        session.removeAttribute("pending2faUser");
        session.removeAttribute("pending2faAttempts");
        if (twoFactorService.enabled(((Number) user.get("customer_id")).intValue())) {
            // Password alone never creates an authenticated account session
            // after the user enables 2FA.
            session.setAttribute("pending2faUser", user);
            session.setAttribute("pending2faAttempts", 0);
            return "redirect:/login/verify";
        }
        session.setAttribute("user", user);
        return "redirect:/dashboard";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) String status,
                            @RequestParam(required = false) String bookmark,
                            HttpSession session,
                            Model model) {
        Map<String, Object> user = currentUser(session);

        if (user == null) {
            return "redirect:/login?reason=dashboard";
        }

        Object customerId = user.get("customer_id");

        model.addAttribute("user", user);
        var savedPlans = savedPlanService.savedPlans(((Number) customerId).intValue());
        model.addAttribute("savedPlans", savedPlans);
        model.addAttribute("savedCount", savedPlans.size());
        model.addAttribute("billingOverview",
                billingService.overview(((Number) customerId).intValue()));
        model.addAttribute("twoFactorEnabled",
                twoFactorService.enabled(((Number) customerId).intValue()));
        model.addAttribute("officialSiteVisits",
                enrollmentReferralService.history(((Number) customerId).intValue()));
        model.addAttribute("officialSiteVisitCount",
                enrollmentReferralService.count(((Number) customerId).intValue()));
        int accountId = ((Number) customerId).intValue();
        model.addAttribute("unreadNotifications", accountActivityService.unreadCount(accountId));
        model.addAttribute("openReportCount", accountActivityService.openReportCount(accountId));
        model.addAttribute("agentRequestCount", accountActivityService.agentRequestCount(accountId));
        model.addAttribute("recentNotifications",
                accountActivityService.notifications(accountId).stream().limit(3).toList());

        model.addAttribute("assessments", db.queryForList(
                "SELECT assessment_id, age, coverage_type, monthly_budget, " +
                "household_size, zip_code, income_range, priority, created_at " +
                "FROM insurance_assessments " +
                "WHERE customer_id = ? ORDER BY created_at DESC",
                customerId
        ));

        model.addAttribute("enrollments", db.queryForList(
                "SELECT e.enrollment_id, e.plan_id, e.household_size, " +
                "e.enrollment_status, e.start_date, e.end_date, e.created_at, " +
                "p.provider_name, p.plan_name, p.coverage_type, " +
                "p.monthly_premium, p.deductible " +
                "FROM enrollments e " +
                "JOIN insurance_plans p ON e.plan_id = p.plan_id " +
                "WHERE e.customer_id = ? " +
                "ORDER BY e.created_at DESC",
                customerId
        ));

        model.addAttribute("extensionRequests", db.queryForList(
                "SELECT request_id, enrollment_id, requested_end_date, " +
                "request_status, created_at FROM extension_requests " +
                "WHERE customer_id = ? ORDER BY created_at DESC",
                customerId
        ));

        model.addAttribute("status", status);
        model.addAttribute("bookmark", bookmark);
        return "dashboard";
    }

    // Plan education is available without a database connection.
    // Do not expose legacy fictional insurance_plans as real Georgia offerings.
    @GetMapping("/plans")
    public String plans(@RequestParam(required = false) String bookmark,
                        HttpSession session, Model model) {
        Map<String, Object> user = currentUser(session);
        model.addAttribute("catalogPlans", GeorgiaPlanCatalog.all());
        model.addAttribute("signedIn", user != null);
        model.addAttribute("savedSlugs", user == null
                ? java.util.Set.of()
                : savedPlanService.savedSlugs(((Number) user.get("customer_id")).intValue()));
        model.addAttribute("bookmark", bookmark);
        return "plans";
    }

    private Map<String, Object> currentUser(HttpSession session) {
        Object user = session.getAttribute("user");

        if (user instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) user;
            return result;
        }

        return null;
    }
}
