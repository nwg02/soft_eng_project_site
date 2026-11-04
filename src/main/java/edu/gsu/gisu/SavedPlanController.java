package edu.gsu.gisu;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/** Signed-in, account-scoped bookmark actions with allowlisted return routes. */
@Controller
public class SavedPlanController {
    private final SavedPlanService service;
    public SavedPlanController(SavedPlanService service) { this.service = service; }

    @GetMapping("/saved-plans")
    public String list(HttpSession session) {
        return customerId(session) == null
                ? "redirect:/login?reason=savedplan"
                : "redirect:/dashboard#saved-plans";
    }

    @PostMapping("/saved-plans")
    public String save(@RequestParam(required = false) String planSlug,
                       @RequestParam(defaultValue = "plans") String returnTo,
                       @RequestParam(required = false) Long assessmentId,
                       HttpSession session) {
        Integer id = customerId(session);
        if (id == null) return "redirect:/login?reason=savedplan";
        return redirect(returnTo, assessmentId,
                service.save(id, planSlug) ? "saved" : "invalid", planSlug);
    }

    @PostMapping("/saved-plans/remove")
    public String remove(@RequestParam(required = false) String planSlug,
                         @RequestParam(defaultValue = "dashboard") String returnTo,
                         @RequestParam(required = false) Long assessmentId,
                         HttpSession session) {
        Integer id = customerId(session);
        if (id == null) return "redirect:/login?reason=savedplan";
        return redirect(returnTo, assessmentId,
                service.remove(id, planSlug) ? "removed" : "invalid", planSlug);
    }

    private String redirect(String target, Long assessmentId, String notice, String slug) {
        if ("dashboard".equals(target)) return "redirect:/dashboard?bookmark=" + notice + "#saved-plans";
        if ("results".equals(target) && assessmentId != null && assessmentId > 0) {
            return "redirect:/assessment/results/" + assessmentId +
                    "?bookmark=" + notice + "#suggested-plans";
        }
        String fragment = GeorgiaPlanCatalog.findBySlug(slug)
                .map(plan -> "#" + plan.slug()).orElse("");
        return "redirect:/plans?bookmark=" + notice + fragment;
    }

    private Integer customerId(HttpSession session) {
        Object principal = session.getAttribute("user");
        if (principal instanceof Map<?, ?> user &&
                user.get("customer_id") instanceof Number id) return id.intValue();
        return null;
    }
}
