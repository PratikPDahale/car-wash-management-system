USE washcare_crm;

ALTER TABLE loyalty_transactions
    ADD COLUMN invoice_id BIGINT NULL,
    ADD UNIQUE KEY uk_loyalty_invoice_transaction (invoice_id, transaction_type),
    ADD CONSTRAINT fk_loyalty_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id);

ALTER TABLE reminders
    ADD COLUMN booking_id BIGINT NULL,
    ADD KEY idx_reminders_booking_id (booking_id),
    ADD CONSTRAINT fk_reminders_booking FOREIGN KEY (booking_id) REFERENCES bookings (id);
