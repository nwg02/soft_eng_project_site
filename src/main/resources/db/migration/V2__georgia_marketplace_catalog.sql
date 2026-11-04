-- GISU Georgia health marketplace catalog
-- Source: Georgia Access Public Use Files (PY2027).
-- Import only verified rows from the official Plan, Rate, and Service Area PUFs.
-- Never treat a sample or estimated premium as a consumer-specific quote.

CREATE TABLE IF NOT EXISTS marketplace_issuers (
    issuer_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_issuer_id VARCHAR(40) NOT NULL,
    issuer_name VARCHAR(180) NOT NULL,
    issuer_website VARCHAR(500),
    source_name VARCHAR(80) NOT NULL DEFAULT 'Georgia Access PUF',
    plan_year SMALLINT NOT NULL,
    source_file VARCHAR(255),
    source_updated_at DATE,
    last_imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_marketplace_issuer_source (source_issuer_id, plan_year)
);

CREATE TABLE IF NOT EXISTS marketplace_plans (
    marketplace_plan_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    issuer_id BIGINT NOT NULL,
    source_plan_id VARCHAR(80) NOT NULL,
    plan_year SMALLINT NOT NULL,
    plan_name VARCHAR(255) NOT NULL,
    marketing_name VARCHAR(255),
    metal_level VARCHAR(30),
    plan_type VARCHAR(40),
    market_type VARCHAR(40) NOT NULL DEFAULT 'individual',
    coverage_type VARCHAR(40) NOT NULL DEFAULT 'medical',
    deductible_individual DECIMAL(10,2),
    deductible_family DECIMAL(10,2),
    max_out_of_pocket_individual DECIMAL(10,2),
    max_out_of_pocket_family DECIMAL(10,2),
    hsa_eligible BOOLEAN,
    summary_of_benefits_url VARCHAR(1000),
    issuer_enrollment_url VARCHAR(1000),
    source_name VARCHAR(80) NOT NULL DEFAULT 'Georgia Access PUF',
    source_file VARCHAR(255),
    source_updated_at DATE,
    data_as_of DATE,
    active_listing BOOLEAN NOT NULL DEFAULT TRUE,
    source_record_json JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_marketplace_plan_issuer FOREIGN KEY (issuer_id)
        REFERENCES marketplace_issuers(issuer_id),
    UNIQUE KEY uq_marketplace_plan_source (source_plan_id, plan_year),
    INDEX idx_marketplace_plan_year_metal (plan_year, metal_level),
    INDEX idx_marketplace_plan_active (active_listing, plan_year)
);

CREATE TABLE IF NOT EXISTS marketplace_plan_service_areas (
    service_area_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    marketplace_plan_id BIGINT NOT NULL,
    state_code CHAR(2) NOT NULL DEFAULT 'GA',
    county_name VARCHAR(100),
    zip_code CHAR(5),
    source_file VARCHAR(255),
    CONSTRAINT fk_service_area_plan FOREIGN KEY (marketplace_plan_id)
        REFERENCES marketplace_plans(marketplace_plan_id) ON DELETE CASCADE,
    INDEX idx_service_area_zip (state_code, zip_code),
    INDEX idx_service_area_county (state_code, county_name)
);

CREATE TABLE IF NOT EXISTS marketplace_plan_rates (
    rate_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    marketplace_plan_id BIGINT NOT NULL,
    county_name VARCHAR(100),
    zip_code CHAR(5),
    age SMALLINT,
    tobacco_status VARCHAR(20),
    monthly_premium DECIMAL(10,2) NOT NULL,
    rate_year SMALLINT NOT NULL,
    source_file VARCHAR(255),
    source_updated_at DATE,
    CONSTRAINT fk_rate_plan FOREIGN KEY (marketplace_plan_id)
        REFERENCES marketplace_plans(marketplace_plan_id) ON DELETE CASCADE,
    INDEX idx_plan_rate_lookup (marketplace_plan_id, zip_code, age, rate_year)
);

CREATE TABLE IF NOT EXISTS saved_marketplace_plans (
    saved_plan_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    marketplace_plan_id BIGINT NOT NULL,
    saved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_saved_plan_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE,
    CONSTRAINT fk_saved_plan_listing FOREIGN KEY (marketplace_plan_id)
        REFERENCES marketplace_plans(marketplace_plan_id) ON DELETE CASCADE,
    UNIQUE KEY uq_customer_saved_plan (customer_id, marketplace_plan_id)
);

-- Feature support tables. Payments and claims are tracking records only;
-- this application does not collect premiums or submit claims to insurers.
CREATE TABLE IF NOT EXISTS payment_history (
    payment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    description VARCHAR(255) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_date DATE,
    payment_status VARCHAR(40) NOT NULL DEFAULT 'recorded',
    external_reference VARCHAR(160),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE,
    INDEX idx_payment_customer_date (customer_id, payment_date)
);

CREATE TABLE IF NOT EXISTS agent_requests (
    agent_request_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    preferred_contact_method VARCHAR(30),
    preferred_contact_time VARCHAR(80),
    topic VARCHAR(255),
    request_status VARCHAR(30) NOT NULL DEFAULT 'requested',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_request_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_notifications (
    notification_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    title VARCHAR(180) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE,
    INDEX idx_notification_customer_date (customer_id, created_at)
);

CREATE TABLE IF NOT EXISTS identity_verification_attempts (
    verification_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    verification_method VARCHAR(40) NOT NULL DEFAULT 'mock',
    verification_status VARCHAR(30) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP NULL,
    CONSTRAINT fk_verification_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS insurance_claim_records (
    claim_record_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    insurer_name VARCHAR(180),
    external_claim_reference VARCHAR(160),
    claim_type VARCHAR(100),
    claim_description TEXT,
    claim_status VARCHAR(40) NOT NULL DEFAULT 'submitted_to_gisu',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_claim_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS support_chat_messages (
    message_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NULL,
    session_key VARCHAR(120),
    sender VARCHAR(20) NOT NULL,
    message_text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE SET NULL,
    INDEX idx_chat_session (session_key, created_at)
);
