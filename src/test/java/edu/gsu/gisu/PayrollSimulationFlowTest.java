package edu.gsu.gisu;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gisu_payroll_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE;DB_CLOSE_DELAY=-1",
        "GEMINI_API_KEY="
})
class PayrollSimulationFlowTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired PayrollSimulationService payroll;

    private record Account(MockHttpSession session, int id) {}
    @SuppressWarnings("unchecked")
    private Account register() throws Exception {
        MvcResult r = mvc.perform(post("/register")
                .param("firstName", "Payroll").param("lastName", "Tester")
                .param("email", UUID.randomUUID() + "@example.test")
                .param("password", "TestPassword123!"))
                .andExpect(redirectedUrl("/dashboard")).andReturn();
        MockHttpSession session = (MockHttpSession) r.getRequest().getSession(false);
        int id = ((Number)((Map<String, Object>) session.getAttribute("user"))
                .get("customer_id")).intValue();
        return new Account(session, id);
    }

    @Test
    void validatesInputAndCalculatesEveryFrequency() throws Exception {
        assertEquals("184.62", PayrollSimulationService.perPaycheck(
                new java.math.BigDecimal("600.00"),
                new java.math.BigDecimal("200.00"), "biweekly").toPlainString());
        assertEquals("92.31", PayrollSimulationService.perPaycheck(
                new java.math.BigDecimal("600.00"),
                new java.math.BigDecimal("200.00"), "weekly").toPlainString());
        assertEquals("200.00", PayrollSimulationService.perPaycheck(
                new java.math.BigDecimal("600.00"),
                new java.math.BigDecimal("200.00"), "semimonthly").toPlainString());
        assertEquals("400.00", PayrollSimulationService.perPaycheck(
                new java.math.BigDecimal("600.00"),
                new java.math.BigDecimal("200.00"), "monthly").toPlainString());
        assertFalse(PayrollSimulationService.validInput("general",
                new java.math.BigDecimal("200.00"), new java.math.BigDecimal("201.00"), "weekly"));
        assertFalse(PayrollSimulationService.validInput("general",
                new java.math.BigDecimal("0.00"), java.math.BigDecimal.ZERO, "weekly"));
        assertFalse(PayrollSimulationService.validInput("general",
                new java.math.BigDecimal("250.111"), java.math.BigDecimal.ZERO, "weekly"));
        assertFalse(PayrollSimulationService.validInput("not-real",
                new java.math.BigDecimal("200"), java.math.BigDecimal.ZERO, "weekly"));
    }

    @Test
    void mockPayrollNeverAltersActualBillingAndIsAccountPrivate() throws Exception {
        Account a = register();
        Account b = register();
        mvc.perform(get("/payroll"))
                .andExpect(redirectedUrl("/login?reason=account"));
        mvc.perform(get("/payroll").session(a.session()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Payroll deductions")))
                .andExpect(content().string(containsString("Create a simulation")));

        mvc.perform(post("/payroll").session(a.session())
                .param("planSlug", "gisu-plan-01")
                .param("monthlyPremium", "600.00")
                .param("employerContribution", "200.00")
                .param("payFrequency", "biweekly"))
                .andExpect(redirectedUrl("/payroll?event=created#payroll-scenarios"));

        assertEquals(1, payroll.scenarioCount(a.id()));
        assertEquals(0, payroll.scenarioCount(b.id()));
        assertEquals("184.62", payroll.scenarios(a.id()).get(0)
                .perPaycheck().toPlainString());
        assertEquals("4800.00", payroll.scenarios(a.id()).get(0)
                .annualEmployee().toPlainString());

        mvc.perform(get("/payroll").session(a.session()))
                .andExpect(content().string(containsString("$184.62")))
                .andExpect(content().string(containsString("SoloCare Gold PPO")));

        mvc.perform(get("/payroll").session(b.session()))
                .andExpect(content().string(not(containsString("$184.62"))))
                .andExpect(content().string(containsString("No simulations yet")));

        Long id = db.queryForObject(
                "SELECT simulation_id FROM payroll_simulations WHERE customer_id = ?",
                Long.class, a.id());
        String today = java.time.LocalDate.now().toString();
        mvc.perform(post("/payroll/" + id + "/simulate").session(b.session())
                .param("payday", today))
                .andExpect(redirectedUrl("/payroll?event=unavailable#payroll-history"));
        assertEquals(0, payroll.history(a.id()).size());

        mvc.perform(post("/payroll/" + id + "/simulate").session(a.session())
                .param("payday", today))
                .andExpect(redirectedUrl("/payroll?event=simulated#payroll-history"));
        assertEquals(1, payroll.history(a.id()).size());
        assertEquals("184.62", payroll.history(a.id()).get(0).amount().toPlainString());

        mvc.perform(post("/payroll/" + id + "/simulate").session(a.session())
                .param("payday", today))
                .andExpect(redirectedUrl("/payroll?event=unavailable#payroll-history"));
        assertEquals(1, payroll.history(a.id()).size());

        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM payments", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM payment_history", Integer.class));
        assertEquals(1, db.queryForObject(
                "SELECT COUNT(*) FROM user_notifications " +
                "WHERE customer_id = ? AND notification_type = 'payroll_demo'",
                Integer.class, a.id()));

        mvc.perform(post("/payroll/" + id + "/delete").session(b.session()))
                .andExpect(redirectedUrl("/payroll?event=unavailable#payroll-scenarios"));
        assertEquals(1, payroll.history(a.id()).size());

        mvc.perform(post("/payroll/" + id + "/delete").session(a.session()))
                .andExpect(redirectedUrl("/payroll?event=deleted#payroll-scenarios"));
        assertEquals(0, payroll.scenarioCount(a.id()));
        assertEquals(0, payroll.history(a.id()).size());
        assertEquals(0, db.queryForObject(
                "SELECT COUNT(*) FROM payroll_demo_deductions WHERE customer_id = ?",
                Integer.class, a.id()));
    }

    @Test
    void badPayrollFormAndInvalidPaycheckDateDoNotWrite() throws Exception {
        Account a = register();
        mvc.perform(post("/payroll").session(a.session())
                .param("planSlug", "general")
                .param("monthlyPremium", "100.00")
                .param("employerContribution", "200.00")
                .param("payFrequency", "monthly"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter an example plan")));
        assertEquals(0, payroll.scenarioCount(a.id()));

        mvc.perform(post("/payroll").session(a.session())
                .param("planSlug", "general")
                .param("monthlyPremium", "100.00")
                .param("employerContribution", "10.00")
                .param("payFrequency", "weekly"))
                .andExpect(redirectedUrl("/payroll?event=created#payroll-scenarios"));
        Long id = db.queryForObject(
                "SELECT simulation_id FROM payroll_simulations WHERE customer_id = ?",
                Long.class, a.id());
        mvc.perform(post("/payroll/" + id + "/simulate").session(a.session())
                .param("payday", java.time.LocalDate.now().plusYears(1).toString()))
                .andExpect(redirectedUrl("/payroll?event=unavailable#payroll-history"));
        assertEquals(0, payroll.history(a.id()).size());
    }
}
