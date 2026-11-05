package edu.gsu.gisu;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Bookmarks official plan documents; never creates coverage or enrollment. */
@Service
public class SavedPlanService {
    private final JdbcTemplate db;
    public SavedPlanService(JdbcTemplate db) { this.db = db; }

    public Set<String> savedSlugs(int customerId) {
        return new LinkedHashSet<>(db.queryForList(
                "SELECT plan_slug FROM saved_curated_plans WHERE customer_id = ? " +
                "ORDER BY saved_at DESC, saved_plan_id DESC", String.class, customerId));
    }

    public List<GeorgiaPlanCatalog.Plan> savedPlans(int customerId) {
        return savedSlugs(customerId).stream()
                .map(GeorgiaPlanCatalog::findBySlug)
                .flatMap(Optional::stream).toList();
    }

    public boolean save(int customerId, String slug) {
        if (GeorgiaPlanCatalog.findBySlug(slug).isEmpty()) return false;
        try {
            db.update("INSERT INTO saved_curated_plans (customer_id, plan_slug) VALUES (?, ?)",
                    customerId, slug);
        } catch (DuplicateKeyException ignored) {
            // Saving the same document twice is safe.
        }
        return true;
    }

    public boolean remove(int customerId, String slug) {
        if (GeorgiaPlanCatalog.findBySlug(slug).isEmpty()) return false;
        db.update("DELETE FROM saved_curated_plans WHERE customer_id = ? AND plan_slug = ?",
                customerId, slug);
        return true;
    }
}
