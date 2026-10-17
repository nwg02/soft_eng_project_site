
package edu.gsu.gisu;

import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
public class WebController {
    private final JdbcTemplate db;

    public WebController(JdbcTemplate db) {
        this.db = db;
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
                           HttpSession session) {
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

            session.setAttribute("user", user);
            return "redirect:/dashboard";
        } catch (DuplicateKeyException ex) {
            model.addAttribute("error",
                    "An account with that email already exists.");
            return "register";
        }
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        Model model,
                        HttpSession session) {
        List<Map<String, Object>> users = db.queryForList(
                "SELECT customer_id, first_name, last_name, email, role, " +
                "password_hash FROM customers WHERE email = ?",
                email.trim().toLowerCase()
        );

        if (users.isEmpty() ||
                !BCrypt.checkpw(password,
                        (String) users.get(0).get("password_hash"))) {
            model.addAttribute("error",
                    "Email or password is incorrect.");
            return "login";
        }

        Map<String, Object> user = users.get(0);
        user.remove("password_hash");
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
                            HttpSession session,
                            Model model) {
        Map<String, Object> user = currentUser(session);

        if (user == null) {
            return "redirect:/login";
        }

        Object customerId = user.get("customer_id");

        model.addAttribute("user", user);

        model.addAttribute("assessments", db.queryForList(
                "SELECT assessment_id, age, coverage_type, monthly_budget, " +
                "household_size, created_at FROM insurance_assessments " +
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

        model.addAttribute("availablePlans", db.queryForList(
                "SELECT plan_id, provider_name, plan_name, coverage_type, " +
                "monthly_premium, deductible FROM insurance_plans " +
                "ORDER BY provider_name, plan_name"
        ));

        model.addAttribute("status", status);
        return "dashboard";
    }

    @GetMapping("/assessment")
    public String assessmentPage(HttpSession session) {
        if (currentUser(session) == null) {
            return "redirect:/login";
        }
        return "assessment";
    }

    @PostMapping("/assessment")
    public String submitAssessment(@RequestParam int age,
                                   @RequestParam String coverageType,
                                   @RequestParam BigDecimal monthlyBudget,
                                   @RequestParam int householdSize,
                                   HttpSession session,
                                   Model model) {
        Map<String, Object> user = currentUser(session);

        if (user == null) {
            return "redirect:/login";
        }

        if (age < 18 || age > 110 ||
                householdSize < 1 || householdSize > 20 ||
                monthlyBudget.compareTo(BigDecimal.ZERO) <= 0 ||
                !(coverageType.equals("individual") ||
                  coverageType.equals("family"))) {
            model.addAttribute("error",
                    "Please check your answers and try again.");
            return "assessment";
        }

        db.update(
                "INSERT INTO insurance_assessments " +
                "(customer_id, age, coverage_type, monthly_budget, " +
                "household_size) VALUES (?, ?, ?, ?, ?)",
                user.get("customer_id"), age, coverageType,
                monthlyBudget, householdSize
        );

        List<Map<String, Object>> plans = db.queryForList(
                "SELECT plan_id, provider_name, plan_name, coverage_type, " +
                "monthly_premium, deductible, description " +
                "FROM insurance_plans WHERE coverage_type = ? " +
                "AND monthly_premium <= ? ORDER BY monthly_premium",
                coverageType, monthlyBudget
        );

        if (plans.isEmpty()) {
            plans = db.queryForList(
                    "SELECT plan_id, provider_name, plan_name, coverage_type, " +
                    "monthly_premium, deductible, description " +
                    "FROM insurance_plans WHERE coverage_type = ? " +
                    "ORDER BY monthly_premium LIMIT 2",
                    coverageType
            );
        }

        model.addAttribute("plans", plans);
        model.addAttribute("saved", true);
        return "assessment";
    }

    @GetMapping("/plans")
    public String plans(@RequestParam(required = false) String error,
                        Model model) {
        model.addAttribute("plans", db.queryForList(
                "SELECT plan_id, provider_name, plan_name, coverage_type, " +
                "monthly_premium, deductible, description " +
                "FROM insurance_plans ORDER BY coverage_type, monthly_premium"
        ));
        model.addAttribute("error", error);
        return "plans";
    }

    @PostMapping("/enroll")
    public String enroll(@RequestParam int planId,
                         @RequestParam(defaultValue = "1") int householdSize,
                         HttpSession session) {
        Map<String, Object> user = currentUser(session);

        if (user == null) {
            return "redirect:/login";
        }

        List<Map<String, Object>> plans = db.queryForList(
                "SELECT plan_id, coverage_type FROM insurance_plans " +
                "WHERE plan_id = ?",
                planId
        );

        if (plans.isEmpty()) {
            return "redirect:/plans?error=invalid";
        }

        String coverageType =
                plans.get(0).get("coverage_type").toString();

        if ("family".equalsIgnoreCase(coverageType)) {
            if (householdSize < 2 || householdSize > 20) {
                return "redirect:/plans?error=household";
            }
        } else {
            householdSize = 1;
        }

        Integer existing = db.queryForObject(
                "SELECT COUNT(*) FROM enrollments " +
                "WHERE customer_id = ? AND plan_id = ? " +
                "AND enrollment_status = 'active'",
                Integer.class, user.get("customer_id"), planId
        );

        if (existing != null && existing > 0) {
            return "redirect:/dashboard?status=exists";
        }

        db.update(
                "INSERT INTO enrollments " +
                "(customer_id, plan_id, enrollment_status, start_date, " +
                "household_size) VALUES (?, ?, 'active', CURRENT_DATE, ?)",
                user.get("customer_id"), planId, householdSize
        );

        return "redirect:/dashboard?status=success";
    }

    @PostMapping("/cancel-enrollment")
    public String cancelEnrollment(@RequestParam int enrollmentId,
                                   HttpSession session) {
        Map<String, Object> user = currentUser(session);

        if (user == null) {
            return "redirect:/login";
        }

        db.update(
                "UPDATE enrollments " +
                "SET enrollment_status = 'cancelled', end_date = CURRENT_DATE " +
                "WHERE enrollment_id = ? AND customer_id = ? " +
                "AND enrollment_status = 'active'",
                enrollmentId, user.get("customer_id")
        );

        return "redirect:/dashboard?status=cancelled";
    }

    @PostMapping("/request-extension")
    public String requestExtension(
            @RequestParam int enrollmentId,
            @RequestParam LocalDate requestedEndDate,
            HttpSession session) {
        Map<String, Object> user = currentUser(session);

        if (user == null) {
            return "redirect:/login";
        }

        if (!requestedEndDate.isAfter(LocalDate.now())) {
            return "redirect:/dashboard?status=extension_error";
        }

        Integer valid = db.queryForObject(
                "SELECT COUNT(*) FROM enrollments " +
                "WHERE enrollment_id = ? AND customer_id = ? " +
                "AND enrollment_status = 'active'",
                Integer.class, enrollmentId, user.get("customer_id")
        );

        if (valid == null || valid == 0) {
            return "redirect:/dashboard?status=extension_error";
        }

        Integer pending = db.queryForObject(
                "SELECT COUNT(*) FROM extension_requests " +
                "WHERE enrollment_id = ? AND customer_id = ? " +
                "AND request_status = 'pending'",
                Integer.class, enrollmentId, user.get("customer_id")
        );

        if (pending != null && pending > 0) {
            return "redirect:/dashboard?status=extension_pending";
        }

        db.update(
                "INSERT INTO extension_requests " +
                "(enrollment_id, customer_id, requested_end_date, request_status) " +
                "VALUES (?, ?, ?, 'pending')",
                enrollmentId, user.get("customer_id"), requestedEndDate
        );

        return "redirect:/dashboard?status=extension_requested";
    }

    @PostMapping("/switch-plan")
    @Transactional
    public String switchPlan(@RequestParam int enrollmentId,
                             @RequestParam int newPlanId,
                             @RequestParam(defaultValue = "1") int householdSize,
                             HttpSession session) {
        Map<String, Object> user = currentUser(session);

        if (user == null) {
            return "redirect:/login";
        }

        List<Map<String, Object>> current = db.queryForList(
                "SELECT plan_id FROM enrollments " +
                "WHERE enrollment_id = ? AND customer_id = ? " +
                "AND enrollment_status = 'active'",
                enrollmentId, user.get("customer_id")
        );

        if (current.isEmpty()) {
            return "redirect:/dashboard?status=switch_error";
        }

        List<Map<String, Object>> target = db.queryForList(
                "SELECT plan_id, coverage_type FROM insurance_plans " +
                "WHERE plan_id = ?",
                newPlanId
        );

        if (target.isEmpty()) {
            return "redirect:/dashboard?status=switch_error";
        }

        int oldPlanId =
                ((Number) current.get(0).get("plan_id")).intValue();

        if (oldPlanId == newPlanId) {
            return "redirect:/dashboard?status=same_plan";
        }

        String coverageType =
                target.get(0).get("coverage_type").toString();

        if ("family".equalsIgnoreCase(coverageType)) {
            if (householdSize < 2 || householdSize > 20) {
                return "redirect:/dashboard?status=switch_household_error";
            }
        } else {
            householdSize = 1;
        }

        Integer alreadyEnrolled = db.queryForObject(
                "SELECT COUNT(*) FROM enrollments " +
                "WHERE customer_id = ? AND plan_id = ? " +
                "AND enrollment_status = 'active'",
                Integer.class, user.get("customer_id"), newPlanId
        );

        if (alreadyEnrolled != null && alreadyEnrolled > 0) {
            return "redirect:/dashboard?status=exists";
        }

        int cancelled = db.update(
                "UPDATE enrollments " +
                "SET enrollment_status = 'cancelled', end_date = CURRENT_DATE " +
                "WHERE enrollment_id = ? AND customer_id = ? " +
                "AND enrollment_status = 'active'",
                enrollmentId, user.get("customer_id")
        );

        if (cancelled != 1) {
            return "redirect:/dashboard?status=switch_error";
        }

        db.update(
                "INSERT INTO enrollments " +
                "(customer_id, plan_id, enrollment_status, start_date, household_size) " +
                "VALUES (?, ?, 'active', CURRENT_DATE, ?)",
                user.get("customer_id"), newPlanId, householdSize
        );

        return "redirect:/dashboard?status=switched";
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
