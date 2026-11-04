package edu.gsu.gisu;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only, account-scoped chat summaries.
 *
 * Local summaries may show user-entered notes to their authenticated owner.
 * Gemini snapshots NEVER include notes, ZIP, age, income, budget, email,
 * names, payment details or other free-text entered by a user.
 */
@Service
public class AccountChatService {
    public enum Topic { NOTIFICATIONS, REPORTS, SAVED_PLANS, ASSESSMENTS, AGENT_REQUESTS, OVERVIEW }
    public record Summary(String localAnswer, String geminiSnapshot) {}

    private final JdbcTemplate db;
    private final SavedPlanService savedPlans;
    private final AccountActivityService activity;

    public AccountChatService(JdbcTemplate db, SavedPlanService savedPlans,
                              AccountActivityService activity) {
        this.db = db;
        this.savedPlans = savedPlans;
        this.activity = activity;
    }

    public Topic detectTopic(String message) {
        String normalized = " " + message.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim() + " ";
        // Avoid intercepting general education questions such as "What is a notification?"
        boolean ownAccount = normalized.contains(" my ") || normalized.contains(" mine ") ||
                normalized.contains(" our ") || normalized.contains(" i have ") ||
                normalized.contains(" i saved ") || normalized.contains(" i submitted ");
        if (!ownAccount) return null;
        if (containsAny(normalized, " notification ", " notifications ", " alerts ", " alert "))
            return Topic.NOTIFICATIONS;
        if (containsAny(normalized, " report ", " reports ", " claim ", " claims ",
                " issue ", " issues ")) return Topic.REPORTS;
        if (containsAny(normalized, " saved plan ", " saved plans ", " bookmarked ",
                " bookmarks ", " shortlist ", " my plans ", " plan i saved "))
            return Topic.SAVED_PLANS;
        if (containsAny(normalized, " assessment ", " assessments ", " preferences ",
                " priority ")) return Topic.ASSESSMENTS;
        if (containsAny(normalized, " agent ", " assistance ", " help request ",
                " requests ")) return Topic.AGENT_REQUESTS;
        if (containsAny(normalized, " dashboard ", " account activity ", " my account ",
                " everything ")) return Topic.OVERVIEW;
        return null;
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) if (text.contains(token)) return true;
        return false;
    }

    public Summary summarize(int customerId, Topic topic) {
        return switch (topic) {
            case NOTIFICATIONS -> notifications(customerId);
            case REPORTS -> reports(customerId);
            case SAVED_PLANS -> plans(customerId);
            case ASSESSMENTS -> assessments(customerId);
            case AGENT_REQUESTS -> agentRequests(customerId);
            case OVERVIEW -> overview(customerId);
        };
    }

    private Summary notifications(int customerId) {
        int unread = activity.unreadCount(customerId);
        int count = number("SELECT COUNT(*) FROM user_notifications WHERE customer_id = ?", customerId);
        List<Map<String, Object>> recent = db.queryForList(
                "SELECT title, message, read_at FROM user_notifications " +
                "WHERE customer_id = ? ORDER BY created_at DESC, notification_id DESC LIMIT 5",
                customerId);
        StringBuilder local = new StringBuilder("You have " + count +
                " GISU notifications (" + unread + " unread).");
        StringBuilder snapshot = new StringBuilder("Notification total=" + count +
                "; unread=" + unread + ". Recent system-generated titles and read flags:");
        for (Map<String, Object> row : recent) {
            String title = String.valueOf(row.get("title"));
            String flag = row.get("read_at") == null ? "unread" : "read";
            local.append("\n- [").append(flag).append("] ").append(title)
                    .append(": ").append(String.valueOf(row.get("message")));
            snapshot.append("\n- ").append(title).append(" (").append(flag).append(")");
        }
        if (count == 0) local.append(" Updates will appear here when you record assistance requests or issue notes.");
        local.append("\nOpen Notifications to mark messages as read.");
        return new Summary(local.toString(), snapshot.toString());
    }

    private Summary reports(int customerId) {
        int count = number("SELECT COUNT(*) FROM insurance_claim_records WHERE customer_id = ?", customerId);
        int open = activity.openReportCount(customerId);
        List<Map<String, Object>> recent = db.queryForList(
                "SELECT claim_category, claim_status, description " +
                "FROM insurance_claim_records WHERE customer_id = ? " +
                "ORDER BY submitted_at DESC, claim_record_id DESC LIMIT 5", customerId);
        StringBuilder local = new StringBuilder("You have " + count +
                " GISU issue notes (" + open + " open). These are not insurer claims.");
        StringBuilder snapshot = new StringBuilder("GISU issue notes=" + count + "; open=" + open +
                ". Recent categories and statuses only (no user-written descriptions):");
        for (Map<String, Object> row : recent) {
            String category = String.valueOf(row.get("claim_category"));
            String status = String.valueOf(row.get("claim_status"));
            local.append("\n- [").append(status).append("] ").append(category)
                    .append(": ").append(shortText(row.get("description"), 115));
            snapshot.append("\n- ").append(allowedCategory(category))
                    .append(" (").append("closed".equals(status) ? "closed" : "open").append(")");
        }
        local.append("\nOpen Reports to update your notes.");
        return new Summary(local.toString(), snapshot.toString());
    }

    private Summary plans(int customerId) {
        List<GeorgiaPlanCatalog.Plan> items = savedPlans.savedPlans(customerId);
        StringBuilder local = new StringBuilder("You have " + items.size() +
                " saved plan documents. These are bookmarks, not coverage or verified quotes.");
        StringBuilder snapshot = new StringBuilder("Saved plan documents=" + items.size() +
                ". Curated catalog facts only:");
        for (GeorgiaPlanCatalog.Plan plan : items.stream().limit(8).toList()) {
            String item = plan.name() + " — " + plan.issuer() + ", " +
                    plan.metal() + " " + plan.type() +
                    ", deductible stated in name " + plan.getDeductibleLabel();
            local.append("\n- ").append(item);
            snapshot.append("\n- ").append(item);
        }
        if (items.size() > 8) local.append("\nSee Dashboard for the full list.");
        local.append("\nExact premiums, network availability and actual policy terms require issuer verification. Use Dashboard to open the official documents.");
        return new Summary(local.toString(), snapshot.toString() +
                "\nDo not infer premiums, network availability, suitability, or actual coverage.");
    }

    private Summary assessments(int customerId) {
        List<Map<String, Object>> latest = db.queryForList(
                "SELECT assessment_id, coverage_type, household_size, monthly_budget, " +
                "zip_code, priority FROM insurance_assessments " +
                "WHERE customer_id = ? ORDER BY assessment_id DESC LIMIT 1", customerId);
        int count = number("SELECT COUNT(*) FROM insurance_assessments WHERE customer_id = ?", customerId);
        if (latest.isEmpty()) return new Summary(
                "You haven't saved an assessment yet. Open Assessment to record your preferences.",
                "Saved assessments=0.");
        Map<String, Object> a = latest.get(0);
        String coverage = String.valueOf(a.get("coverage_type"));
        String priority = String.valueOf(a.get("priority"));
        String summary = "You have " + count + " saved assessment(s). Your latest records " +
                coverage + " coverage, household size " + a.get("household_size") +
                ", ZIP " + (a.get("zip_code") == null ? "not recorded" : a.get("zip_code")) +
                ", monthly premium budget $" + a.get("monthly_budget") +
                ", and comparison priority " + priority + "." +
                "\nThese preferences do not confirm eligibility or personalized prices. Reopen the assessment from Dashboard.";
        String sanitized = "Saved assessments=" + count + ". Latest coverage type=" +
                safeCoverage(coverage) + "; comparison priority=" + safePriority(priority) +
                ". ZIP, age, household size, income, budget, and other personal fields intentionally withheld.";
        return new Summary(summary, sanitized);
    }

    private Summary agentRequests(int customerId) {
        List<Map<String, Object>> recent = db.queryForList(
                "SELECT contact_method, preferred_time, topic FROM agent_requests " +
                "WHERE customer_id = ? ORDER BY agent_request_id DESC LIMIT 5",
                customerId);
        int count = activity.agentRequestCount(customerId);
        StringBuilder local = new StringBuilder("You have " + count +
                " demo assistance request(s). No agent has been contacted or scheduled.");
        StringBuilder snapshot = new StringBuilder("Demo agent requests=" + count +
                ". Contact preferences only; no user-entered topics or notes:");
        for (Map<String, Object> row : recent) {
            String method = String.valueOf(row.get("contact_method"));
            String time = String.valueOf(row.get("preferred_time"));
            local.append("\n- ").append(shortText(row.get("topic"), 100))
                    .append(" (preferred: ").append(method).append(", ").append(time).append(")");
            snapshot.append("\n- ").append("phone".equals(method) ? "phone" : "email")
                    .append(", ").append(allowedTime(time));
        }
        local.append("\nFor real help, contact Georgia Access or a licensed agent.");
        return new Summary(local.toString(), snapshot.toString());
    }

    private Summary overview(int customerId) {
        int saved = savedPlans.savedPlans(customerId).size();
        int assessments = number("SELECT COUNT(*) FROM insurance_assessments WHERE customer_id = ?", customerId);
        int notes = number("SELECT COUNT(*) FROM insurance_claim_records WHERE customer_id = ?", customerId);
        int open = activity.openReportCount(customerId);
        int requests = activity.agentRequestCount(customerId);
        int unread = activity.unreadCount(customerId);
        String facts = saved + " saved plan documents, " + assessments + " assessments, " +
                notes + " issue notes (" + open + " open), " +
                requests + " demo assistance requests and " + unread + " unread notifications.";
        return new Summary("Your GISU dashboard has " + facts +
                "\nYou can ask me about any category for more details. These are classroom account records, not confirmed insurance coverage.",
                "Account overview: " + facts + " No personal notes, identity or demographic details supplied.");
    }

    private int number(String sql, int customerId) {
        Integer value = db.queryForObject(sql, Integer.class, customerId);
        return value == null ? 0 : value;
    }

    private String shortText(Object input, int max) {
        if (input == null) return "(none)";
        String text = String.valueOf(input).replaceAll("[\\r\\n\\t]+", " ").trim();
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }

    private String allowedCategory(String input) {
        return switch (input) {
            case "Billing or premium question", "Coverage or benefits question",
                 "Provider network question", "Enrollment process question",
                 "Other insurance issue" -> input;
            default -> "Other issue";
        };
    }
    private String safeCoverage(String value) {
        return "family".equals(value) ? "family" : "individual";
    }
    private String safePriority(String value) {
        return GeorgiaPlanCatalog.validPriority(value) ? value : "balanced";
    }
    private String allowedTime(String value) {
        return switch (value) {
            case "morning", "afternoon", "evening" -> value;
            default -> "no preference";
        };
    }
}
