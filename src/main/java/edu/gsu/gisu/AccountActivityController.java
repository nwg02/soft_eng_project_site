package edu.gsu.gisu;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Educational, account-private support and tracking pages.
 * None of these actions communicate with insurers or licensed agents.
 */
@Controller
public class AccountActivityController {
    private static final Map<String, String> ASSISTANCE_TOPICS = Map.of(
            "understand", "Understanding insurance terms",
            "compare", "Comparing plan documents",
            "marketplace", "Marketplace and enrollment questions",
            "other", "Other insurance question"
    );
    private static final Map<String, String> REPORT_CATEGORIES = Map.of(
            "billing", "Billing or premium question",
            "coverage", "Coverage or benefits question",
            "network", "Provider network question",
            "enrollment", "Enrollment process question",
            "other", "Other insurance issue"
    );

    private final AccountActivityService activity;
    private final CoverageReminderService reminders;

    public AccountActivityController(AccountActivityService activity,
                                     CoverageReminderService reminders) {
        this.activity = activity;
        this.reminders = reminders;
    }

    @GetMapping("/agent-assistance")
    public String assistance(HttpSession session, Model model) {
        Integer id = customerId(session);
        if (id == null) return "redirect:/login?reason=account";
        assistanceModel(id, model);
        return "agent-assistance";
    }

    @PostMapping("/agent-assistance")
    public String requestAssistance(@RequestParam(required = false) String contactMethod,
                                    @RequestParam(required = false) String preferredTime,
                                    @RequestParam(required = false) String topic,
                                    @RequestParam(required = false) String notes,
                                    HttpSession session, Model model) {
        Integer id = customerId(session);
        if (id == null) return "redirect:/login?reason=account";

        String cleanNotes = notes == null ? "" : notes.trim();
        if (!("email".equals(contactMethod) || "phone".equals(contactMethod)) ||
                !("any".equals(preferredTime) || "morning".equals(preferredTime) ||
                  "afternoon".equals(preferredTime) || "evening".equals(preferredTime)) ||
                (topic == null || !ASSISTANCE_TOPICS.containsKey(topic)) || cleanNotes.length() > 350) {
            model.addAttribute("error", "Select a contact preference, time, and topic. " +
                    "Keep optional notes under 350 characters.");
            assistanceModel(id, model);
            return "agent-assistance";
        }

        String requestTopic = ASSISTANCE_TOPICS.get(topic) +
                (cleanNotes.isBlank() ? "" : " - " + cleanNotes);
        activity.createAgentRequest(id, contactMethod, preferredTime, requestTopic);
        return "redirect:/agent-assistance?event=recorded";
    }

    @GetMapping("/reports")
    public String reports(HttpSession session, Model model) {
        Integer id = customerId(session);
        if (id == null) return "redirect:/login?reason=account";
        reportsModel(id, model);
        return "reports";
    }

    @PostMapping("/reports")
    public String createReport(@RequestParam(required = false) String category,
                               @RequestParam(required = false) String description,
                               HttpSession session, Model model) {
        Integer id = customerId(session);
        if (id == null) return "redirect:/login?reason=account";

        String details = description == null ? "" : description.trim();
        if (category == null || !REPORT_CATEGORIES.containsKey(category) ||
                details.length() < 5 || details.length() > 800) {
            model.addAttribute("error", "Choose a report category and enter " +
                    "between 5 and 800 characters without sensitive personal information.");
            reportsModel(id, model);
            return "reports";
        }

        activity.createReport(id, REPORT_CATEGORIES.get(category), details);
        return "redirect:/reports?event=created";
    }

    @PostMapping("/reports/{reportId}/close")
    public String closeReport(@PathVariable long reportId, HttpSession session) {
        Integer id = customerId(session);
        if (id == null) return "redirect:/login?reason=account";
        boolean success = reportId > 0 &&
                activity.updateReportStatus(id, reportId, "open", "closed");
        return "redirect:/reports?event=" + (success ? "closed" : "unavailable");
    }

    @PostMapping("/reports/{reportId}/reopen")
    public String reopenReport(@PathVariable long reportId, HttpSession session) {
        Integer id = customerId(session);
        if (id == null) return "redirect:/login?reason=account";
        boolean success = reportId > 0 &&
                activity.updateReportStatus(id, reportId, "closed", "open");
        return "redirect:/reports?event=" + (success ? "reopened" : "unavailable");
    }

    @GetMapping("/notifications")
    public String notifications(HttpSession session, Model model) {
        Integer id = customerId(session);
        if (id == null) return "redirect:/login?reason=account";
        reminders.refresh(id);
        model.addAttribute("renewalReminders", reminders.renewals(id));
        model.addAttribute("notifications", activity.notifications(id));
        model.addAttribute("unreadCount", activity.unreadCount(id));
        return "notifications";
    }

    @PostMapping("/notifications/renewals")
    public String createRenewalReminder(@RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                         LocalDate renewalDate, HttpSession session) {
        Integer id = customerId(session);
        if (id == null) return "redirect:/login?reason=account";
        if (!CoverageReminderService.validRenewalDate(renewalDate))
            return "redirect:/notifications?event=invalid-date";
        try {
            reminders.addRenewal(id, renewalDate);
        } catch (IllegalArgumentException ex) {
            return "redirect:/notifications?event=invalid-date";
        }
        return "redirect:/notifications?event=reminder-saved";
    }

    @PostMapping("/notifications/renewals/{reminderId}/delete")
    public String deleteRenewalReminder(@PathVariable long reminderId, HttpSession session) {
        Integer id = customerId(session);
        if (id == null) return "redirect:/login?reason=account";
        boolean removed = reminders.deleteRenewal(id, reminderId);
        return "redirect:/notifications?event=" + (removed ? "reminder-deleted" : "unavailable");
    }

    @PostMapping("/notifications/{notificationId}/read")
    public String read(@PathVariable long notificationId, HttpSession session) {
        Integer id = customerId(session);
        if (id == null) return "redirect:/login?reason=account";
        boolean marked = notificationId > 0 && activity.markRead(id, notificationId);
        return "redirect:/notifications?event=" + (marked ? "read" : "unavailable");
    }

    @PostMapping("/notifications/read-all")
    public String readAll(HttpSession session) {
        Integer id = customerId(session);
        if (id == null) return "redirect:/login?reason=account";
        activity.markAllRead(id);
        return "redirect:/notifications?event=all-read";
    }

    private void assistanceModel(int customerId, Model model) {
        model.addAttribute("agentRequests", activity.agentRequests(customerId));
    }

    private void reportsModel(int customerId, Model model) {
        model.addAttribute("reports", activity.reports(customerId));
    }

    private Integer customerId(HttpSession session) {
        Object principal = session.getAttribute("user");
        if (principal instanceof Map<?, ?> user &&
                user.get("customer_id") instanceof Number id) {
            return id.intValue();
        }
        return null;
    }
}
