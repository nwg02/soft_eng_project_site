package edu.gsu.gisu;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** User-only enrollment preparation. Never submits to insurers or activates coverage. */
@Service
public class EnrollmentRequestService {
    private final JdbcTemplate db;
    public EnrollmentRequestService(JdbcTemplate db) {
        this.db = db;
    }

    public static boolean validStartDate(LocalDate date) {
        LocalDate today = LocalDate.now();
        return date != null && !date.isBefore(today) &&
                !date.isAfter(today.plusYears(1));
    }

    @Transactional
    public boolean save(int customerId, String slug, LocalDate desiredStart) {
        if (GeorgiaPlanCatalog.findBySlug(slug).isEmpty() || !validStartDate(desiredStart))
            return false;
        Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM gisu_enrollment_requests WHERE customer_id = ?",
                Integer.class, customerId);
        if (count != null && count >= 20) return false;
        db.update(
                "INSERT INTO gisu_enrollment_requests " +
                "(customer_id, plan_slug, requested_start, request_status) " +
                "VALUES (?, ?, ?, 'ready')",
                customerId, slug, Date.valueOf(desiredStart));
        var plan = GeorgiaPlanCatalog.findBySlug(slug).orElseThrow();
        db.update("INSERT INTO user_notifications " +
                        "(customer_id, notification_type, title, message) " +
                        "VALUES (?, 'policy_update', 'Plan selection updated', ?)",
                customerId, plan.name() + " is saved to your enrollment requests.");
        return true;
    }

    public List<Map<String, Object>> requests(int customerId) {
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT request_id, plan_slug, requested_start, request_status, created_at " +
                "FROM gisu_enrollment_requests WHERE customer_id = ? " +
                "ORDER BY created_at DESC, request_id DESC LIMIT 20",
                customerId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> record : rows) {
            var plan = GeorgiaPlanCatalog.findBySlug(
                    String.valueOf(record.get("plan_slug")));
            if (plan.isEmpty()) continue;
            Map<String, Object> item = new LinkedHashMap<>(record);
            item.put("planName", plan.get().name());
            item.put("issuer", plan.get().issuer());
            item.put("slug", plan.get().slug());
            out.add(item);
        }
        return out;
    }

    public int count(int customerId) {
        Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM gisu_enrollment_requests WHERE customer_id = ?",
                Integer.class, customerId);
        return count == null ? 0 : count;
    }

    public boolean delete(int customerId, long requestId) {
        if (requestId <= 0) return false;
        return db.update(
                "DELETE FROM gisu_enrollment_requests " +
                "WHERE customer_id = ? AND request_id = ?", customerId, requestId) == 1;
    }
}
