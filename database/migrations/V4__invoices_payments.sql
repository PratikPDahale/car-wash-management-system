USE washcare_crm;

ALTER TABLE payments
    ADD COLUMN idempotency_key VARCHAR(64) NULL;

UPDATE payments
SET idempotency_key = CONCAT('legacy-', id)
WHERE idempotency_key IS NULL;

ALTER TABLE payments
    MODIFY COLUMN idempotency_key VARCHAR(64) NOT NULL,
    ADD UNIQUE KEY uk_payments_idempotency_key (idempotency_key);
