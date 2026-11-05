package edu.gsu.gisu;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Creates account-only in-app alerts for planned bill due dates and user-entered
 * policy renewal dates. Alerts are generated on dashboard/notifications visits.
 */
@Service
public class CoverageReminderService {
    private final JdbcTemplate db;

    public CoverageReminderService(JdbcTemplate db) {
        this.db = db;
    }

    public static boolean validRenewalDate(LocalDate date) {
        LocalDate today = LocalDate.now();
        return date != null && !date.isBefore(today) &&
                !date.isAfter(today.plusYears(2));
    }

    @Transactional
    public void addRenewal(int customerId, LocalDate date) {
        if (!validRenewalDate(date))
            throw new IllegalArgumentException("Invalid renewal date");
        Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM gisu_renewal_reminders WHERE customer_id = ?",
                Integer.class, customerId);
        if (count != null && count >= 30)
            throw new IllegalArgumentException("Too many renewal reminders");
        db.update("INSERT INTO gisu_renewal_reminders " +
                "(customer_id, renewal_date, label) VALUES (?, ?, 'Policy renewal')",
                customerId, Date.valueOf(date));
    }

    public List<Map<String, Object>> renewals(int customerId) {
        return db.queryForList(
                "SELECT reminder_id, renewal_date, label, notified_at FROM gisu_renewal_reminders " +
                "WHERE customer_id = ? ORDER BY renewal_date, reminder_id LIMIT 30",
                customerId);
    }

    public boolean deleteRenewal(int customerId, long id) {
        if (id <= 0) return false;
        return db.update("DELETE FROM gisu_renewal_reminders " +
                "WHERE reminder_id = ? AND customer_id = ?", id, customerId) == 1;
    }

    @Transactional
    public void refresh(int customerId) {
        LocalDate today = LocalDate.now();

        // A billing due-date alert is generated at most once per planned bill.
        List<Map<String, Object>> bills = db.queryForList(
                "SELECT payment_history_id, payment_date, amount " +
                "FROM payment_history WHERE customer_id = ? AND payment_status = 'planned' " +
                "AND payment_date >= ? AND payment_date <= ? LIMIT 100",
                customerId, Date.valueOf(today), Date.valueOf(today.plusDays(7)));
        for (Map<String, Object> bill : bills) {
            long recordId = ((Number) bill.get("payment_history_id")).longValue();
            Integer existing = db.queryForObject(
                    "SELECT COUNT(*) FROM gisu_notification_receipts " +
                    "WHERE customer_id = ? AND source_type = 'payment_due' AND source_id = ?",
                    Integer.class, customerId, recordId);
            if (existing != null && existing > 0) continue;
            int inserted = db.update(
                    "INSERT INTO gisu_notification_receipts (customer_id, source_type, source_id) " +
                    "SELECT ?, 'payment_due', ? WHERE NOT EXISTS " +
                    "(SELECT 1 FROM gisu_notification_receipts WHERE customer_id = ? " +
                    "AND source_type = 'payment_due' AND source_id = ?)",
                    customerId, recordId, customerId, recordId);
            if (inserted == 1) {
                String amount = "$" + ((BigDecimal) bill.get("amount")).toPlainString();
                addNotification(customerId, "payment_due", "Payment due soon",
                        "Your planned " + amount + " payment is due on " +
                                ((Date) bill.get("payment_date")).toLocalDate() + ".");
            }
        }

        // User-specified renewals generate a notification within 30 days.
        List<Map<String, Object>> renewals = db.queryForList(
                "SELECT reminder_id, renewal_date FROM gisu_renewal_reminders " +
                "WHERE customer_id = ? AND notified_at IS NULL " +
                "AND renewal_date >= ? AND renewal_date <= ? LIMIT 30",
                customerId, Date.valueOf(today), Date.valueOf(today.plusDays(30)));
        for (Map<String, Object> reminder : renewals) {
            long id = ((Number) reminder.get("reminder_id")).longValue();
            int updated = db.update(
                    "UPDATE gisu_renewal_reminders SET notified_at = CURRENT_TIMESTAMP " +
                    "WHERE customer_id = ? AND reminder_id = ? AND notified_at IS NULL",
                    customerId, id);
            if (updated == 1) {
                addNotification(customerId, "policy_renewal", "Policy renewal reminder",
                        "Your saved renewal date is " +
                                ((Date) reminder.get("renewal_date")).toLocalDate() + ".");
            }
        }
    }

    private void addNotification(int id, String kind, String title, String message) {
        db.update("INSERT INTO user_notifications " +
                "(customer_id, notification_type, title, message) VALUES (?, ?, ?, ?)",
                id, kind, title, message);
    }
}
