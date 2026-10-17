CREATE DATABASE IF NOT EXISTS gisu;
USE gisu;

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assessment_customer FOREIGN KEY (customer_id)
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

CREATE TABLE IF NOT EXISTS payments (
    payment_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    plan_id INT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_status VARCHAR(30) NOT NULL DEFAULT 'pending',
    payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id),
    CONSTRAINT fk_payment_plan FOREIGN KEY (plan_id)
        REFERENCES insurance_plans(plan_id)
);

CREATE TABLE IF NOT EXISTS enrollments (
    enrollment_id INT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    plan_id INT NOT NULL,
    enrollment_status VARCHAR(30) NOT NULL DEFAULT 'active',
    start_date DATE NOT NULL,
    end_date DATE NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_enrollment_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id),

    CONSTRAINT fk_enrollment_plan
        FOREIGN KEY (plan_id)
        REFERENCES insurance_plans(plan_id)
);

INSERT INTO insurance_plans
    (provider_name, plan_name, coverage_type, monthly_premium, deductible, description)
SELECT 'GISU Health', 'Individual Basic', 'individual', 150.00, 3000.00,
       'Basic individual coverage for the prototype.'
WHERE NOT EXISTS (SELECT 1 FROM insurance_plans WHERE plan_name = 'Individual Basic');

INSERT INTO insurance_plans
    (provider_name, plan_name, coverage_type, monthly_premium, deductible, description)
SELECT 'GISU Health', 'Individual Plus', 'individual', 220.00, 1500.00,
       'Expanded individual coverage for the prototype.'
WHERE NOT EXISTS (SELECT 1 FROM insurance_plans WHERE plan_name = 'Individual Plus');

INSERT INTO insurance_plans
    (provider_name, plan_name, coverage_type, monthly_premium, deductible, description)
SELECT 'GISU Health', 'Family Basic', 'family', 390.00, 3500.00,
       'Basic family coverage for the prototype.'
WHERE NOT EXISTS (SELECT 1 FROM insurance_plans WHERE plan_name = 'Family Basic');

INSERT INTO insurance_plans
    (provider_name, plan_name, coverage_type, monthly_premium, deductible, description)
SELECT 'GISU Health', 'Family Plus', 'family', 480.00, 1800.00,
       'Expanded family coverage for the prototype.'
WHERE NOT EXISTS (SELECT 1 FROM insurance_plans WHERE plan_name = 'Family Plus');
