-- Run ONCE on production MySQL after V3, before deploying saved-plan code.
-- A saved bookmark is NOT an enrollment, quote or insurance policy.
CREATE TABLE IF NOT EXISTS saved_curated_plans (
    saved_plan_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    plan_slug VARCHAR(64) NOT NULL,
    saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_saved_curated_customer_slug (customer_id, plan_slug),
    CONSTRAINT fk_saved_curated_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);
