package edu.gsu.gisu;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Manually entered billing notes. GISU does not connect to a payment provider,
 * issue insurance invoices, charge a card, or verify external transactions.
 */
@Service
public class BillingService {
    private static final Map<String, String> CATEGORIES = Map.of(
            "premium", "Insurance premium",
            "copay", "Copay or coinsurance",
            "prescription", "Prescription",
            "other", "Other coverage-related expense"
    );

    public record Entry(long id, String category, BigDecimal amount, LocalDate date,
                        String status) {
        public String getAmountLabel() {
            return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
        }
        public boolean isPlanned() { return "planned".equals(status); }
        public boolean isRecordedPaid() { return "recorded_paid".equals(status); }
    }

    public record Overview(BigDecimal planned, BigDecimal paid, int plannedCount,
                           int paidCount) {
        public String getPlannedLabel() {
            return NumberFormat.getCurrencyInstance(Locale.US).format(planned);
        }
        public String getPaidLabel() {
            return NumberFormat.getCurrencyInstance(Locale.US).format(paid);
        }
    }

    private final JdbcTemplate db;

    public BillingService(JdbcTemplate db) {
        this.db = db;
    }

    public List<Entry> entries(int customerId) {
        return db.query(
                "SELECT payment_history_id, source_description, amount, payment_date, " +
                "payment_status FROM payment_history WHERE customer_id = ? " +
                "AND payment_status IN ('planned', 'recorded_paid') " +
                "ORDER BY payment_date DESC, payment_history_id DESC LIMIT 100",
                (rs, rowNum) -> new Entry(
                        rs.getLong("payment_history_id"),
                        safeCategory(rs.getString("source_description")),
                        rs.getBigDecimal("amount"),
                        rs.getDate("payment_date").toLocalDate(),
                        rs.getString("payment_status")),
                customerId);
    }

    public Overview overview(int customerId) {
        List<Map<String, Object>> values = db.queryForList(
                "SELECT payment_status, COUNT(*) AS num_entries, COALESCE(SUM(amount), 0) AS total " +
                "FROM payment_history WHERE customer_id = ? " +
                "AND payment_status IN ('planned', 'recorded_paid') " +
                "GROUP BY payment_status",
                customerId);
        BigDecimal planned = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        int plannedCount = 0;
        int paidCount = 0;
        for (Map<String, Object> row : values) {
            BigDecimal total = (BigDecimal) row.get("total");
            int count = ((Number) row.get("num_entries")).intValue();
            if ("planned".equals(row.get("payment_status"))) {
                planned = total;
                plannedCount = count;
            } else if ("recorded_paid".equals(row.get("payment_status"))) {
                paid = total;
                paidCount = count;
            }
        }
        return new Overview(planned, paid, plannedCount, paidCount);
    }

    public static boolean validCategory(String category) {
        return category != null && CATEGORIES.containsKey(category);
    }

    public static boolean validStatus(String status) {
        return "planned".equals(status) || "recorded_paid".equals(status);
    }

    public static boolean validAmount(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0 &&
                amount.compareTo(new BigDecimal("100000.00")) <= 0 &&
                amount.scale() <= 2;
    }

    public static boolean validDate(LocalDate date, String status) {
        if (date == null || !validStatus(status)) return false;
        LocalDate today = LocalDate.now();
        return !date.isBefore(LocalDate.of(2020, 1, 1)) &&
                !date.isAfter("planned".equals(status)
                        ? today.plusYears(2) : today);
    }

    @Transactional
    public void add(int customerId, String category, BigDecimal amount,
                    LocalDate date, String status) {
        if (!validCategory(category) || !validAmount(amount) || !validDate(date, status))
            throw new IllegalArgumentException("Invalid manual payment record");
        db.update(
                "INSERT INTO payment_history " +
                "(customer_id, amount, currency, payment_date, payment_status, source_description) " +
                "VALUES (?, ?, 'USD', ?, ?, ?)",
                customerId, amount, Date.valueOf(date), status, CATEGORIES.get(category));
        notifyAccount(customerId, "Billing record saved",
                "A " + ("planned".equals(status) ? "planned expense" : "self-recorded payment") +
                " was added to your private GISU billing log. No transaction was processed.");
    }

    @Transactional
    public boolean markPaid(int customerId, long recordId) {
        if (recordId <= 0) return false;
        int changed = db.update(
                "UPDATE payment_history SET payment_status = 'recorded_paid', " +
                "payment_date = CURRENT_DATE WHERE payment_history_id = ? " +
                "AND customer_id = ? AND payment_status = 'planned'",
                recordId, customerId);
        if (changed == 1) {
            notifyAccount(customerId, "Billing record marked paid",
                    "You marked a planned expense as paid in GISU. " +
                    "This is a personal record, not payment confirmation from an insurer.");
        }
        return changed == 1;
    }

    @Transactional
    public boolean delete(int customerId, long recordId) {
        if (recordId <= 0) return false;
        return db.update(
                "DELETE FROM payment_history WHERE payment_history_id = ? " +
                "AND customer_id = ? AND payment_status IN ('planned', 'recorded_paid')",
                recordId, customerId) == 1;
    }

    private void notifyAccount(int customerId, String title, String message) {
        db.update("INSERT INTO user_notifications " +
                        "(customer_id, notification_type, title, message) " +
                        "VALUES (?, 'billing', ?, ?)",
                customerId, title, message);
    }

    private String safeCategory(String value) {
        if (value != null && CATEGORIES.containsValue(value)) return value;
        return "Other coverage-related expense";
    }
}
