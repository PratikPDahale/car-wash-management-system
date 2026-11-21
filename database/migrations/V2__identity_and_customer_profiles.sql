USE washcare_crm;

ALTER TABLE users
    ADD COLUMN full_name VARCHAR(160) NOT NULL DEFAULT 'Shop User' AFTER id;
