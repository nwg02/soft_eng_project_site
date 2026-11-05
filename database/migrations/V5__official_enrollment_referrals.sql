-- GISU curated-plan external enrollment referrals. Apply once on
-- production MySQL after V4 and only when deploying the new feature.
-- These records track click-throughs, NOT applications, enrollment or coverage.
CREATE TABLE IF NOT EXISTS enrollment_referrals (
    referral_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    plan_slug VARCHAR(64) NOT NULL,
    destination VARCHAR(24) NOT NULL,
    clicked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_referral_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);
