package edu.gsu.gisu;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * An official-site handoff, not a GISU enrollment or coverage confirmation.
 * Only destinations resolved from the curated catalog can be followed.
 */
@Controller
public class OfficialEnrollmentController {
    private static final String GEORGIA_ACCESS = "https://georgiaaccess.gov/";
    private final EnrollmentReferralService referrals;

    public OfficialEnrollmentController(EnrollmentReferralService referrals) {
        this.referrals = referrals;
    }

    @GetMapping("/enrollment/plan/{slug}")
    public String plan(@PathVariable String slug, HttpSession session, Model model) {
        var plan = GeorgiaPlanCatalog.findBySlug(slug);
        if (plan.isEmpty()) return "redirect:/plans?enrollment=invalid";
        model.addAttribute("plan", plan.get());
        model.addAttribute("signedIn", accountId(session) != null);
        model.addAttribute("marketplaceUrl", GEORGIA_ACCESS);
        return "official-enrollment";
    }

    @PostMapping("/enrollment/referrals")
    public String continueToOfficialSite(@RequestParam(required = false) String planSlug,
                                         @RequestParam(required = false) String destination,
                                         HttpSession session) {
        var plan = GeorgiaPlanCatalog.findBySlug(planSlug);
        if (plan.isEmpty() ||
                !("issuer".equals(destination) || "georgia-access".equals(destination))) {
            return "redirect:/plans?enrollment=invalid";
        }

        Integer account = accountId(session);
        if (account != null) {
            referrals.record(account, plan.get().slug(), destination);
        }

        // Destination is chosen from server-owned, fixed official URLs,
        // NEVER a URL supplied in the user's request.
        String officialUrl = "issuer".equals(destination)
                ? plan.get().shopUrl() : GEORGIA_ACCESS;
        return "redirect:" + officialUrl;
    }

    private Integer accountId(HttpSession session) {
        Object object = session.getAttribute("user");
        if (object instanceof Map<?, ?> user &&
                user.get("customer_id") instanceof Number id) return id.intValue();
        return null;
    }
}
