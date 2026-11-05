-- GISU Georgia health marketplace: additive schema migration.
-- Apply once to the existing gisu database before deploying application code
-- that uses these tables. This migration preserves legacy tables and records.

ALTER TABLE enrollments
    ADD COLUMN household_size INT NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS extension_requests (
    request_id INT AUTO_INCREMENT PRIMARY KEY,
    enrollment_id INT NOT NULL,
    customer_id INT NOT NULL,
    requested_end_date DATE NOT NULL,
    request_status VARCHAR(30) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_extension_enrollment
        FOREIGN KEY (enrollment_id) REFERENCES enrollments(enrollment_id),
    CONSTRAINT fk_extension_customer
        FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);

CREATE TABLE IF NOT EXISTS marketplace_issuers (
    issuer_id INT AUTO_INCREMENT PRIMARY KEY,
    issuer_name VARCHAR(160) NOT NULL,
    issuer_slug VARCHAR(160) NOT NULL UNIQUE,
    official_website_url VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS marketplace_plans (
    marketplace_plan_id INT AUTO_INCREMENT PRIMARY KEY,
    issuer_id INT NOT NULL,
    external_plan_id VARCHAR(160) NOT NULL,
    plan_year SMALLINT NOT NULL,
    plan_name VARCHAR(240) NOT NULL,
    metal_level VARCHAR(40),
    plan_type VARCHAR(80),
    market_type VARCHAR(40) NOT NULL DEFAULT 'individual',
    deductible_individual DECIMAL(12,2),
    deductible_family DECIMAL(12,2),
    oop_max_individual DECIMAL(12,2),
    oop_max_family DECIMAL(12,2),
    hsa_eligible BOOLEAN,
    official_plan_url VARCHAR(1200),
    source_name VARCHAR(160) NOT NULL,
    source_file VARCHAR(500),
    source_updated_at DATE,
    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    source_record JSON,
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uq_marketplace_plan_source (issuer_id, external_plan_id, plan_year),
    CONSTRAINT fk_marketplace_plan_issuer
        FOREIGN KEY (issuer_id) REFERENCES marketplace_issuers(issuer_id)
);

CREATE TABLE IF NOT EXISTS marketplace_plan_service_areas (
    service_area_id INT AUTO_INCREMENT PRIMARY KEY,
    marketplace_plan_id INT NOT NULL,
    state_code CHAR(2) NOT NULL DEFAULT 'GA',
    county_name VARCHAR(120),
    zip_code CHAR(5),
    CONSTRAINT fk_service_area_plan
        FOREIGN KEY (marketplace_plan_id)
        REFERENCES marketplace_plans(marketplace_plan_id)
        ON DELETE CASCADE,
    KEY idx_service_area_zip (zip_code),
    KEY idx_service_area_county (county_name)
);

CREATE TABLE IF NOT EXISTS marketplace_plan_rates (
    rate_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    marketplace_plan_id INT NOT NULL,
    zip_code CHAR(5) NOT NULL,
    county_name VARCHAR(120),
    age SMALLINT NOT NULL,
    tobacco_status VARCHAR(30),
    rate_amount DECIMAL(12,2) NOT NULL,
    rate_effective_date DATE,
    source_name VARCHAR(160) NOT NULL,
    source_file VARCHAR(500),
    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rate_plan
        FOREIGN KEY (marketplace_plan_id)
        REFERENCES marketplace_plans(marketplace_plan_id)
        ON DELETE CASCADE,
    KEY idx_rate_lookup (zip_code, age, marketplace_plan_id)
);

CREATE TABLE IF NOT EXISTS saved_marketplace_plans (
    saved_plan_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    marketplace_plan_id INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_saved_customer_plan (customer_id, marketplace_plan_id),
    CONSTRAINT fk_saved_plan_customer
        FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_saved_plan_listing
        FOREIGN KEY (marketplace_plan_id)
        REFERENCES marketplace_plans(marketplace_plan_id)
        ON DELETE CASCADE
);

-- A click-through or application start is not proof of insurance coverage.
CREATE TABLE IF NOT EXISTS enrollment_intents (
    enrollment_intent_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    marketplace_plan_id INT NOT NULL,
    intent_status VARCHAR(40) NOT NULL DEFAULT 'started',
    official_destination_url VARCHAR(1200) NOT NULL,
    household_size INT NOT NULL DEFAULT 1,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_intent_customer
        FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    CONSTRAINT fk_intent_plan
        FOREIGN KEY (marketplace_plan_id)
        REFERENCES marketplace_plans(marketplace_plan_id)
);

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
    CONSTRAINT fk_payment_history_customer
        FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS agent_requests (
    agent_request_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    contact_method VARCHAR(40) NOT NULL,
    preferred_time VARCHAR(120),
    topic VARCHAR(500),
    request_status VARCHAR(40) NOT NULL DEFAULT 'submitted',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_request_customer
        FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_notifications (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    notification_type VARCHAR(60) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_customer
        FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS identity_verification_attempts (
    verification_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    verification_method VARCHAR(60) NOT NULL DEFAULT 'mock',
    verification_status VARCHAR(40) NOT NULL DEFAULT 'not_started',
    result_message VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_verification_customer
        FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS insurance_claim_records (
    claim_record_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    external_claim_reference VARCHAR(160),
    claim_category VARCHAR(100),
    description TEXT,
    claim_status VARCHAR(40) NOT NULL DEFAULT 'reported',
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_claim_customer
        FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS support_chat_messages (
    message_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NULL,
    session_reference VARCHAR(160),
    sender VARCHAR(20) NOT NULL,
    message_text TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_customer
        FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
        ON DELETE SET NULL
);
