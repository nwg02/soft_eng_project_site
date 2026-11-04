package edu.gsu.gisu;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Entirely simulated payroll deductions: no employer, payroll provider,
 * insurance billing, payroll taxes, bank, or payment API integration.
 */
@Service
public class PayrollSimulationService {
    private final JdbcTemplate db;

    public PayrollSimulationService(JdbcTemplate db) {
        this.db = db;
    }

    public record Scenario(long id, String planSlug, String planLabel,
                           BigDecimal monthlyPremium, BigDecimal employerContribution,
                           String payFrequency, int periodsPerYear,
                           BigDecimal employeeMonthly, BigDecimal perPaycheck,
                           BigDecimal annualEmployee) {
        public String getPremiumLabel() { return usd(monthlyPremium); }
        public String getEmployerLabel() { return usd(employerContribution); }
        public String getEmployeeMonthlyLabel() { return usd(employeeMonthly); }
        public String getPerPaycheckLabel() { return usd(perPaycheck); }
        public String getAnnualLabel() { return usd(annualEmployee); }
        public String getFrequencyLabel() {
            return switch (payFrequency) {
                case "weekly" -> "Weekly (52 paychecks/year)";
                case "biweekly" -> "Every two weeks (26/year)";
                case "semimonthly" -> "Twice a month (24/year)";
                default -> "Monthly (12/year)";
            };
        }
    }
    public record DemoDeduction(long id, long simulationId, String planLabel,
                                LocalDate payday, BigDecimal amount) {
        public String getAmountLabel() { return usd(amount); }
    }

    public static boolean validFrequency(String frequency) {
        return "weekly".equals(frequency) || "biweekly".equals(frequency) ||
                "semimonthly".equals(frequency) || "monthly".equals(frequency);
    }

    public static int periods(String frequency) {
        return switch (frequency) {
            case "weekly" -> 52;
            case "biweekly" -> 26;
            case "semimonthly" -> 24;
            case "monthly" -> 12;
            default -> throw new IllegalArgumentException("Unknown pay schedule");
        };
    }

    public static boolean validInput(String planSlug, BigDecimal premium,
                                     BigDecimal employer, String frequency) {
        return ("general".equals(planSlug) ||
                GeorgiaPlanCatalog.findBySlug(planSlug).isPresent()) &&
                premium != null && employer != null &&
                premium.scale() <= 2 && employer.scale() <= 2 &&
                premium.compareTo(BigDecimal.ZERO) > 0 &&
                premium.compareTo(new BigDecimal("100000")) <= 0 &&
                employer.compareTo(BigDecimal.ZERO) >= 0 &&
                employer.compareTo(premium) <= 0 &&
                validFrequency(frequency);
    }

    public static BigDecimal perPaycheck(BigDecimal monthlyPremium,
                                        BigDecimal employerContribution, String frequency) {
        if (!validInput("general", monthlyPremium, employerContribution, frequency))
            throw new IllegalArgumentException("Invalid payroll inputs");
        return monthlyPremium.subtract(employerContribution)
                .multiply(BigDecimal.valueOf(12))
                .divide(BigDecimal.valueOf(periods(frequency)), 2, RoundingMode.HALF_UP);
    }

    public static boolean validPayday(LocalDate day) {
        if (day == null) return false;
        LocalDate today = LocalDate.now();
        return !day.isBefore(today.minusYears(1)) &&
                !day.isAfter(today.plusMonths(6));
    }

    @Transactional
    public long create(int customerId, String slug, BigDecimal premium,
                       BigDecimal employer, String frequency) {
        if (!validInput(slug, premium, employer, frequency))
            throw new IllegalArgumentException("Invalid payroll simulation inputs");
        Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM payroll_simulations WHERE customer_id = ?",
                Integer.class, customerId);
        if (count != null && count >= 30)
            throw new IllegalArgumentException("Maximum of 30 simulations per account");
        KeyHolder keys = new GeneratedKeyHolder();
        db.update(conn -> {
            PreparedStatement statement = conn.prepareStatement(
                    "INSERT INTO payroll_simulations " +
                    "(customer_id, plan_slug, monthly_premium, employer_contribution, pay_frequency) " +
                    "VALUES (?, ?, ?, ?, ?)", new String[]{"simulation_id"});
            statement.setInt(1, customerId);
            statement.setString(2, slug);
            statement.setBigDecimal(3, premium);
            statement.setBigDecimal(4, employer);
            statement.setString(5, frequency);
            return statement;
        }, keys);
        if (keys.getKey() == null) throw new IllegalStateException("Simulation ID missing");
        return keys.getKey().longValue();
    }

    public List<Scenario> scenarios(int customerId) {
        return db.query("SELECT simulation_id, plan_slug, monthly_premium, " +
                "employer_contribution, pay_frequency FROM payroll_simulations " +
                "WHERE customer_id = ? ORDER BY simulation_id DESC LIMIT 30",
                (rs, n) -> {
                    BigDecimal monthly = rs.getBigDecimal("monthly_premium");
                    BigDecimal employer = rs.getBigDecimal("employer_contribution");
                    BigDecimal userShare = monthly.subtract(employer);
                    String frequency = rs.getString("pay_frequency");
                    return new Scenario(rs.getLong("simulation_id"),
                            rs.getString("plan_slug"), label(rs.getString("plan_slug")),
                            monthly, employer, frequency, periods(frequency), userShare,
                            perPaycheck(monthly, employer, frequency),
                            userShare.multiply(BigDecimal.valueOf(12)));
                }, customerId);
    }

    @Transactional
    public boolean simulate(int customerId, long scenarioId, LocalDate payday) {
        if (scenarioId <= 0 || !validPayday(payday)) return false;
        List<Scenario> ownerScenarios = scenarios(customerId);
        Scenario selected = ownerScenarios.stream()
                .filter(s -> s.id() == scenarioId).findFirst().orElse(null);
        if (selected == null) return false;
        try {
            db.update("INSERT INTO payroll_demo_deductions " +
                            "(simulation_id, customer_id, payday, deduction_amount) " +
                            "VALUES (?, ?, ?, ?)",
                    scenarioId, customerId, Date.valueOf(payday), selected.perPaycheck());
            db.update("INSERT INTO user_notifications " +
                            "(customer_id, notification_type, title, message) " +
                            "VALUES (?, 'payroll_demo', 'Payroll simulation recorded', " +
                            "'A fictional paycheck deduction was saved in your GISU demo log. No wages or payments moved.')",
                    customerId);
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false; // one scenario cannot log the same demo payday twice
        }
    }

    @Transactional
    public boolean deleteScenario(int customerId, long id) {
        if (id <= 0) return false;
        // Child entries are removed by the scenario foreign key cascade.
        return db.update("DELETE FROM payroll_simulations " +
                "WHERE customer_id = ? AND simulation_id = ?", customerId, id) == 1;
    }

    public List<DemoDeduction> history(int customerId) {
        return db.query("SELECT d.deduction_id, d.simulation_id, d.payday, d.deduction_amount, " +
                        "s.plan_slug FROM payroll_demo_deductions d " +
                        "JOIN payroll_simulations s ON s.simulation_id = d.simulation_id " +
                        "AND s.customer_id = d.customer_id " +
                        "WHERE d.customer_id = ? ORDER BY d.payday DESC, d.deduction_id DESC LIMIT 100",
                (rs, n) -> new DemoDeduction(
                        rs.getLong("deduction_id"), rs.getLong("simulation_id"),
                        label(rs.getString("plan_slug")),
                        rs.getDate("payday").toLocalDate(),
                        rs.getBigDecimal("deduction_amount")),
                customerId);
    }

    public int scenarioCount(int customerId) {
        Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM payroll_simulations WHERE customer_id = ?",
                Integer.class, customerId);
        return count == null ? 0 : count;
    }

    private static String label(String slug) {
        return "general".equals(slug) ? "General hypothetical plan" :
                GeorgiaPlanCatalog.findBySlug(slug)
                        .map(GeorgiaPlanCatalog.Plan::name)
                        .orElse("Archived document reference");
    }

    private static String usd(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(value);
    }
}
