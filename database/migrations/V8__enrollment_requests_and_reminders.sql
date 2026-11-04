-- V8: Small internal enrollment-request workflow and account reminder support.
-- Apply after V7 only during a reviewed production rollout.
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
CREATE TABLE IF NOT EXISTS gisu_renewal_reminders (
    reminder_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    renewal_date DATE NOT NULL,
    label VARCHAR(80) NOT NULL DEFAULT 'Policy renewal',
    notified_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gisu_renewal_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);
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
