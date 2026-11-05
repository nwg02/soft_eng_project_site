-- Local development schema only. Never apply to Railway or production MySQL.
-- No fictional plan records are inserted: the public plan explorer uses verified issuer docs.
CREATE TABLE IF NOT EXISTS customers (
    customer_id INT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(80) NOT NULL,
    last_name VARCHAR(80) NOT NULL,
    email VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'customer',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS insurance_assessments (
    assessment_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    age INT NOT NULL,
    coverage_type VARCHAR(20) NOT NULL,
    monthly_budget DECIMAL(10,2) NOT NULL,
    household_size INT NOT NULL,
    zip_code VARCHAR(5),
    income_range VARCHAR(40) NOT NULL DEFAULT 'prefer_not',
    priority VARCHAR(40) NOT NULL DEFAULT 'balanced',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id) ON DELETE CASCADE
);

-- Upgrades existing local H2 databases created before the new assessment fields.
-- H2 supports IF NOT EXISTS, so these are safe on repeated local app starts.
ALTER TABLE insurance_assessments ADD COLUMN IF NOT EXISTS zip_code VARCHAR(5);
ALTER TABLE insurance_assessments ADD COLUMN IF NOT EXISTS income_range VARCHAR(40) DEFAULT 'prefer_not' NOT NULL;
ALTER TABLE insurance_assessments ADD COLUMN IF NOT EXISTS priority VARCHAR(40) DEFAULT 'balanced' NOT NULL;

-- Saving a curated insurer document never represents real coverage.
CREATE TABLE IF NOT EXISTS saved_curated_plans (
    saved_plan_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    plan_slug VARCHAR(64) NOT NULL,
    saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_saved_curated_customer_slug UNIQUE (customer_id, plan_slug),
    CONSTRAINT fk_saved_curated_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

-- Tracking visits to official enrollment pages; never coverage confirmation.
CREATE TABLE IF NOT EXISTS enrollment_referrals (
    referral_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    plan_slug VARCHAR(64) NOT NULL,
    destination VARCHAR(24) NOT NULL,
    clicked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_referral_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS insurance_plans (
    plan_id INT AUTO_INCREMENT PRIMARY KEY,
    provider_name VARCHAR(120) NOT NULL,
    plan_name VARCHAR(120) NOT NULL,
    coverage_type VARCHAR(20) NOT NULL,
    monthly_premium DECIMAL(10,2) NOT NULL,
    deductible DECIMAL(10,2) NOT NULL,
    description VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS enrollments (
    enrollment_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    plan_id INT NOT NULL,
    household_size INT NOT NULL DEFAULT 1,
    enrollment_status VARCHAR(30) NOT NULL DEFAULT 'active',
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (plan_id) REFERENCES insurance_plans(plan_id)
);

CREATE TABLE IF NOT EXISTS payments (
    payment_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    plan_id INT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_status VARCHAR(30) NOT NULL DEFAULT 'pending',
    payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (plan_id) REFERENCES insurance_plans(plan_id)
);

-- User-entered educational billing records; NOT insurer invoices or verified payments.
-- Matches database/migrations/V2__georgia_marketplace_schema.sql (MySQL).
CREATE TABLE IF NOT EXISTS payment_history (
    payment_history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    external_reference VARCHAR(160),
    amount DECIMAL(12,2),
    currency CHAR(3) NOT NULL DEFAULT 'USD',
    payment_date DATE,
    payment_status VARCHAR(40),
    source_description VARCHAR(300),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_local_payment_history_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS extension_requests (
    request_id INT AUTO_INCREMENT PRIMARY KEY,
    enrollment_id INT NOT NULL,
    customer_id INT NOT NULL,
    requested_end_date DATE NOT NULL,
    request_status VARCHAR(30) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (enrollment_id) REFERENCES enrollments(enrollment_id),
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);



-- Classroom-only identity-verification simulation. No identity documents are collected.
CREATE TABLE IF NOT EXISTS identity_verification_attempts (
    verification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    verification_method VARCHAR(60) NOT NULL DEFAULT 'mock',
    verification_status VARCHAR(40) NOT NULL DEFAULT 'not_started',
    result_message VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_local_verification_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

-- Optional real authenticator (TOTP) login verification; stored encrypted at rest.
CREATE TABLE IF NOT EXISTS account_totp_settings (
    customer_id INT PRIMARY KEY,
    secret_ciphertext VARCHAR(600) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_used_step BIGINT NOT NULL DEFAULT -1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    enabled_at TIMESTAMP,
    CONSTRAINT fk_local_totp_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

-- One-time recovery codes are bcrypt hashes, never stored in plaintext.
CREATE TABLE IF NOT EXISTS account_totp_recovery_codes (
    recovery_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    recovery_hash VARCHAR(150) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_local_recovery_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

-- In-app classroom assistance requests. No external agent receives these records.
CREATE TABLE IF NOT EXISTS agent_requests (
    agent_request_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    contact_method VARCHAR(40) NOT NULL,
    preferred_time VARCHAR(120),
    topic VARCHAR(500),
    request_status VARCHAR(40) NOT NULL DEFAULT 'demo_recorded',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_request_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

-- Private, in-app alerts only. No email, SMS, or scheduled delivery.
CREATE TABLE IF NOT EXISTS user_notifications (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    notification_type VARCHAR(60) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    read_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_notification_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

-- Reports are user-authored issue notes, NOT claims sent to an insurer.
CREATE TABLE IF NOT EXISTS insurance_claim_records (
    claim_record_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    external_claim_reference VARCHAR(160),
    claim_category VARCHAR(100),
    description CLOB,
    claim_status VARCHAR(40) NOT NULL DEFAULT 'open',
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_local_claim_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);


-- AI analysis requires a separate, explicit per-assessment opt-in.
-- Results only reference existing curated documents, never live quotes.
CREATE TABLE IF NOT EXISTS assessment_ai_reviews (
    assessment_id INT PRIMARY KEY,
    customer_id INT NOT NULL,
    summary_text VARCHAR(2400) NOT NULL,
    plan_slugs VARCHAR(300) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_local_review_assessment FOREIGN KEY (assessment_id)
        REFERENCES insurance_assessments(assessment_id) ON DELETE CASCADE,
    CONSTRAINT fk_local_review_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

-- Independent, entirely hypothetical payroll-deduction calculations.
CREATE TABLE IF NOT EXISTS payroll_simulations (
    simulation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    plan_slug VARCHAR(64) NOT NULL,
    monthly_premium DECIMAL(10,2) NOT NULL,
    employer_contribution DECIMAL(10,2) NOT NULL,
    pay_frequency VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_local_sim_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

-- Simulated paycheck entries are not payments or proof of coverage.
CREATE TABLE IF NOT EXISTS payroll_demo_deductions (
    deduction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    simulation_id BIGINT NOT NULL,
    customer_id INT NOT NULL,
    payday DATE NOT NULL,
    deduction_amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_local_sim_payday UNIQUE (simulation_id, payday),
    CONSTRAINT fk_local_deduction_sim FOREIGN KEY (simulation_id)
        REFERENCES payroll_simulations(simulation_id) ON DELETE CASCADE,
    CONSTRAINT fk_local_deduction_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

-- Internal enrollment requests, not official insurance applications.
CREATE TABLE IF NOT EXISTS gisu_enrollment_requests (
    request_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    plan_slug VARCHAR(64) NOT NULL,
    requested_start DATE NOT NULL,
    request_status VARCHAR(32) NOT NULL DEFAULT 'ready',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gisu_request_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

-- A user-configured renewal reminder. Notifications are shown in the app.
CREATE TABLE IF NOT EXISTS gisu_renewal_reminders (
    reminder_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    renewal_date DATE NOT NULL,
    label VARCHAR(80) NOT NULL DEFAULT 'Policy renewal',
    notified_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gisu_renewal_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

-- Dedupes billing due reminders across logins/page refreshes.
CREATE TABLE IF NOT EXISTS gisu_notification_receipts (
    receipt_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_gisu_notification_receipt UNIQUE (customer_id, source_type, source_id),
    CONSTRAINT fk_gisu_receipt_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);
