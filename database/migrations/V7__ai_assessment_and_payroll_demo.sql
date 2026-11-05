-- GISU Sprint 4: consent-based AI reviews and mock payroll simulations.
-- Apply to production MySQL ONLY after V6, following DB backup and deployment review.
-- No API keys or user data are migrated. Neither table represents real coverage
-- or an insurer/employer payroll or billing integration.
CREATE TABLE IF NOT EXISTS assessment_ai_reviews (
    assessment_id INT PRIMARY KEY,
    customer_id INT NOT NULL,
    summary_text VARCHAR(2400) NOT NULL,
    plan_slugs VARCHAR(300) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_assessment FOREIGN KEY (assessment_id)
        REFERENCES insurance_assessments(assessment_id) ON DELETE CASCADE,
    CONSTRAINT fk_review_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS payroll_simulations (
    simulation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    plan_slug VARCHAR(64) NOT NULL,
    monthly_premium DECIMAL(10,2) NOT NULL,
    employer_contribution DECIMAL(10,2) NOT NULL,
    pay_frequency VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sim_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS payroll_demo_deductions (
    deduction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    simulation_id BIGINT NOT NULL,
    customer_id INT NOT NULL,
    payday DATE NOT NULL,
    deduction_amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sim_payday UNIQUE (simulation_id, payday),
    CONSTRAINT fk_deduction_sim FOREIGN KEY (simulation_id)
        REFERENCES payroll_simulations(simulation_id) ON DELETE CASCADE,
    CONSTRAINT fk_deduction_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);
