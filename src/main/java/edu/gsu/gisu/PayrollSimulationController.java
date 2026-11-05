package edu.gsu.gisu;

import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Classroom payroll simulation only, NEVER employer payroll or payments.
 */
@Controller
public class PayrollSimulationController {
    private final PayrollSimulationService payroll;

    public PayrollSimulationController(PayrollSimulationService payroll) {
        this.payroll = payroll;
    }

    @GetMapping("/payroll")
    public String page(HttpSession session, Model model) {
        Integer id = currentCustomer(session);
        if (id == null) return "redirect:/login?reason=account";
        populate(model, id);
        return "payroll";
    }

    @PostMapping("/payroll")
    public String create(@RequestParam(required = false) String planSlug,
                         @RequestParam(required = false) BigDecimal monthlyPremium,
                         @RequestParam(required = false) BigDecimal employerContribution,
                         @RequestParam(required = false) String payFrequency,
                         HttpSession session, Model model) {
        Integer id = currentCustomer(session);
        if (id == null) return "redirect:/login?reason=account";
        if (!PayrollSimulationService.validInput(
                planSlug, monthlyPremium, employerContribution, payFrequency)) {
            model.addAttribute("error", "Enter an example plan, a monthly premium above $0, " +
                    "an employer contribution between $0 and the premium, and a pay frequency. " +
                    "Amounts may use up to two decimal places.");
            populate(model, id);
            return "payroll";
        }
        try {
            payroll.create(id, planSlug, monthlyPremium, employerContribution, payFrequency);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", "Maximum 30 saved payroll simulations per account.");
            populate(model, id);
            return "payroll";
        }
        return "redirect:/payroll?event=created#payroll-scenarios";
    }

    @PostMapping("/payroll/{scenarioId}/simulate")
    public String simulate(@PathVariable long scenarioId,
                           @RequestParam(required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payday,
                           HttpSession session) {
        Integer id = currentCustomer(session);
        if (id == null) return "redirect:/login?reason=account";
        boolean saved = payroll.simulate(id, scenarioId, payday);
        return "redirect:/payroll?event=" + (saved ? "simulated" : "unavailable") +
                "#payroll-history";
    }

    @PostMapping("/payroll/{scenarioId}/delete")
    public String delete(@PathVariable long scenarioId, HttpSession session) {
        Integer id = currentCustomer(session);
        if (id == null) return "redirect:/login?reason=account";
        boolean deleted = payroll.deleteScenario(id, scenarioId);
        return "redirect:/payroll?event=" + (deleted ? "deleted" : "unavailable") +
                "#payroll-scenarios";
    }

    private void populate(Model model, int customerId) {
        model.addAttribute("catalogPlans", GeorgiaPlanCatalog.all());
        model.addAttribute("payrollScenarios", payroll.scenarios(customerId));
        model.addAttribute("payrollHistory", payroll.history(customerId));
        model.addAttribute("today", LocalDate.now().toString());
    }

    private Integer currentCustomer(HttpSession session) {
        Object principal = session.getAttribute("user");
        if (principal instanceof Map<?, ?> user &&
                user.get("customer_id") instanceof Number id) return id.intValue();
        return null;
    }
}
