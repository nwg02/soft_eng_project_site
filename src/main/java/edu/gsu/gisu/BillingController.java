package edu.gsu.gisu;

import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Private manual expense log, never a payment or insurance billing processor. */
@Controller
public class BillingController {
    private final BillingService billing;

    public BillingController(BillingService billing) {
        this.billing = billing;
    }

    @GetMapping("/billing")
    public String billingPage(HttpSession session, Model model) {
        Integer customerId = accountId(session);
        if (customerId == null) return "redirect:/login?reason=account";
        populate(model, customerId);
        return "billing";
    }

    @PostMapping("/billing")
    public String add(@RequestParam(required = false) String category,
                      @RequestParam(required = false) String paymentStatus,
                      @RequestParam(required = false) BigDecimal amount,
                      @RequestParam(required = false)
                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate entryDate,
                      HttpSession session, Model model) {
        Integer customerId = accountId(session);
        if (customerId == null) return "redirect:/login?reason=account";
        if (!BillingService.validCategory(category) ||
                !BillingService.validStatus(paymentStatus) ||
                !BillingService.validAmount(amount) ||
                !BillingService.validDate(entryDate, paymentStatus)) {
            model.addAttribute("error",
                    "Enter a category, an amount between $0.01 and $100,000.00 " +
                    "(up to two decimal places), and a valid date. " +
                    "Recorded payments cannot have future dates.");
            populate(model, customerId);
            return "billing";
        }
        billing.add(customerId, category, amount, entryDate, paymentStatus);
        return "redirect:/billing?event=added#billing-history";
    }

    @PostMapping("/billing/{id}/paid")
    public String markPaid(@PathVariable long id, HttpSession session) {
        Integer customerId = accountId(session);
        if (customerId == null) return "redirect:/login?reason=account";
        boolean updated = billing.markPaid(customerId, id);
        return "redirect:/billing?event=" + (updated ? "paid" : "unavailable") +
                "#billing-history";
    }

    @PostMapping("/billing/{id}/delete")
    public String delete(@PathVariable long id, HttpSession session) {
        Integer customerId = accountId(session);
        if (customerId == null) return "redirect:/login?reason=account";
        boolean deleted = billing.delete(customerId, id);
        return "redirect:/billing?event=" + (deleted ? "deleted" : "unavailable") +
                "#billing-history";
    }

    private void populate(Model model, int customerId) {
        List<BillingService.Entry> rows = billing.entries(customerId);
        model.addAttribute("plannedEntries", rows.stream()
                .filter(BillingService.Entry::isPlanned).toList());
        model.addAttribute("paidEntries", rows.stream()
                .filter(BillingService.Entry::isRecordedPaid).toList());
        model.addAttribute("billingOverview", billing.overview(customerId));
        model.addAttribute("today", LocalDate.now().toString());
    }

    private Integer accountId(HttpSession session) {
        Object principal = session.getAttribute("user");
        if (principal instanceof Map<?, ?> user &&
                user.get("customer_id") instanceof Number id) {
            return id.intValue();
        }
        return null;
    }
}
