package edu.gsu.gisu;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assessment saves preference answers and suggests official documents to review.
 * It never claims ZIP eligibility, premium quote, subsidy eligibility, or enrollment.
 */
@Controller
public class AssessmentController {
    private static final String GA_ZIP_PATTERN = "^(?:3[01]\\d{3}|39[89]\\d{2})$";
    private static final Set<String> INCOME_RANGES = Set.of(
            "prefer_not", "under_30000", "30000_60000",
            "60000_100000", "over_100000"
    );
    private final JdbcTemplate db;

    public AssessmentController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping("/assessment")
    public String form(HttpSession session) {
        return currentCustomerId(session) == null ? "redirect:/login" : "assessment";
    }

    @PostMapping("/assessment")
    public String submit(@RequestParam(required = false) Integer age,
                         @RequestParam(required = false) String zipCode,
                         @RequestParam(required = false) String coverageType,
                         @RequestParam(required = false) Integer householdSize,
                         @RequestParam(required = false) BigDecimal monthlyBudget,
                         @RequestParam(required = false, defaultValue = "prefer_not") String incomeRange,
                         @RequestParam(required = false) String priority,
                         HttpSession session, Model model) {
        Integer customerId = currentCustomerId(session);
        if (customerId == null) return "redirect:/login";

        if (age == null || age < 18 || age > 110 ||
                zipCode == null || !zipCode.matches(GA_ZIP_PATTERN) ||
                householdSize == null || householdSize < 1 || householdSize > 20 ||
                coverageType == null ||
                !("individual".equals(coverageType) || "family".equals(coverageType)) ||
                ("family".equals(coverageType) && householdSize < 2) ||
                monthlyBudget == null || monthlyBudget.compareTo(BigDecimal.ZERO) < 0 ||
                monthlyBudget.compareTo(new BigDecimal("10000")) > 0 ||
                !INCOME_RANGES.contains(incomeRange) ||
                !GeorgiaPlanCatalog.validPriority(priority)) {
            model.addAttribute("error",
                    "Please provide a five-digit Georgia ZIP, valid age and household details, " +
                    "a monthly budget from $0 to $10,000, and your coverage priority. " +
                    "Family coverage requires at least two household members.");
            return "assessment";
        }

        KeyHolder keys = new GeneratedKeyHolder();
        db.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO insurance_assessments " +
                    "(customer_id, age, coverage_type, monthly_budget, household_size, " +
                    "zip_code, income_range, priority) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, customerId);
            statement.setInt(2, age);
            statement.setString(3, coverageType);
            statement.setBigDecimal(4, monthlyBudget);
            statement.setInt(5, householdSize);
            statement.setString(6, zipCode);
            statement.setString(7, incomeRange);
            statement.setString(8, priority);
            return statement;
        }, keys);

        Number newId = keys.getKey();
        if (newId == null) {
            throw new IllegalStateException("Assessment record was not returned by database");
        }
        return "redirect:/assessment/results/" + newId.longValue();
    }

    @GetMapping("/assessment/results/{id}")
    public String results(@PathVariable long id, HttpSession session, Model model) {
        Integer customerId = currentCustomerId(session);
        if (customerId == null) return "redirect:/login";

        List<Map<String, Object>> saved = db.queryForList(
                "SELECT assessment_id, age, coverage_type, household_size, " +
                "monthly_budget, zip_code, income_range, priority, created_at " +
                "FROM insurance_assessments WHERE assessment_id = ? AND customer_id = ?",
                id, customerId);
        if (saved.isEmpty()) return "redirect:/dashboard?status=assessment_not_found";

        Map<String, Object> assessment = saved.get(0);
        String priority = String.valueOf(assessment.get("priority"));
        if (!GeorgiaPlanCatalog.validPriority(priority)) priority = "balanced";
        model.addAttribute("assessment", assessment);
        model.addAttribute("priority", priority);
        model.addAttribute("priorityExplanation", GeorgiaPlanCatalog.explanation(priority));
        model.addAttribute("suggestions", GeorgiaPlanCatalog.suggestions(priority));
        return "assessment-results";
    }

    private Integer currentCustomerId(HttpSession session) {
        Object principal = session.getAttribute("user");
        if (principal instanceof Map<?, ?> user &&
                user.get("customer_id") instanceof Number value) {
            return value.intValue();
        }
        return null;
    }
}
