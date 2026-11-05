package edu.gsu.gisu;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records signed-in click-throughs to official external enrollment websites.
 * A visit does not mean the user applied, qualified for or obtained coverage.
 */
@Service
public class EnrollmentReferralService {
    private final JdbcTemplate db;

    public EnrollmentReferralService(JdbcTemplate db) {
        this.db = db;
    }

    public void record(int customerId, String slug, String destination) {
        if (GeorgiaPlanCatalog.findBySlug(slug).isEmpty() ||
                !("issuer".equals(destination) || "georgia-access".equals(destination))) {
            throw new IllegalArgumentException("Unknown official enrollment destination");
        }
        db.update("INSERT INTO enrollment_referrals " +
                        "(customer_id, plan_slug, destination) VALUES (?, ?, ?)",
                customerId, slug, destination);
    }

    public List<Map<String, Object>> history(int customerId) {
        List<Map<String, Object>> records = db.queryForList(
                "SELECT plan_slug, destination, clicked_at FROM enrollment_referrals " +
                "WHERE customer_id = ? ORDER BY clicked_at DESC, referral_id DESC LIMIT 12",
                customerId);
        List<Map<String, Object>> view = new ArrayList<>();
        for (Map<String, Object> record : records) {
            var plan = GeorgiaPlanCatalog.findBySlug(
                    String.valueOf(record.get("plan_slug")));
            if (plan.isEmpty()) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("slug", plan.get().slug());
            item.put("planName", plan.get().name());
            item.put("issuer", plan.get().issuer());
            item.put("destination", "issuer".equals(record.get("destination"))
                    ? "Insurer website" : "Georgia Access");
            item.put("clickedAt", record.get("clicked_at"));
            view.add(item);
        }
        return view;
    }

    public int count(int customerId) {
        Integer n = db.queryForObject(
                "SELECT COUNT(*) FROM enrollment_referrals WHERE customer_id = ?",
                Integer.class, customerId);
        return n == null ? 0 : n;
    }
}
