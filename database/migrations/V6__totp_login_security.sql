-- GISU optional authenticator-app (TOTP) login verification.
-- Apply ONCE to production MySQL after V5. Back up the database first.
-- No security keys, authenticator secrets or recovery codes belong in Git.
-- Set GISU_TOTP_ENCRYPTION_KEY (base64 of 32 random bytes) on Railway
-- BEFORE enabling 2FA for any production accounts. Keep the key stable.
CREATE TABLE IF NOT EXISTS account_totp_settings (
    customer_id INT PRIMARY KEY,
    secret_ciphertext VARCHAR(600) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_used_step BIGINT NOT NULL DEFAULT -1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    enabled_at TIMESTAMP NULL,
    CONSTRAINT fk_totp_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS account_totp_recovery_codes (
    recovery_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id INT NOT NULL,
    recovery_hash VARCHAR(150) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_totp_recovery_customer FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id) ON DELETE CASCADE
);

-- Mock identity-verification attempt history already exists from V2.
