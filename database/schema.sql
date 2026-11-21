CREATE DATABASE IF NOT EXISTS washcare_crm
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE washcare_crm;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(160) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    CONSTRAINT chk_users_role CHECK (role IN ('OWNER', 'STAFF', 'CUSTOMER')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB;

CREATE TABLE notification_preferences (
    user_id BIGINT NOT NULL,
    enabled_categories VARCHAR(255) NOT NULL,
    quiet_hours_start TIME NULL,
    quiet_hours_end TIME NULL,
    global_muted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    category VARCHAR(20) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    title VARCHAR(120) NOT NULL,
    message VARCHAR(500) NOT NULL,
    target_section VARCHAR(30) NOT NULL,
    target_id BIGINT NULL,
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    KEY idx_notifications_user_available_created (user_id, available_at, created_at),
    KEY idx_notifications_user_read (user_id, read_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_notifications_category CHECK (
        category IN ('BOOKING', 'PAYMENT', 'LOYALTY', 'OFFER', 'REMINDER', 'REVIEW', 'SYSTEM', 'ANNOUNCEMENT')),
    CONSTRAINT chk_notifications_priority CHECK (priority IN ('NORMAL', 'IMPORTANT'))
) ENGINE=InnoDB;

CREATE TABLE customers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    name VARCHAR(160) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    email VARCHAR(255) NULL,
    notes VARCHAR(1000) NULL,
    registration_source VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_customers_user_id (user_id),
    KEY idx_customers_phone (phone),
    KEY idx_customers_email (email),
    CONSTRAINT fk_customers_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_customers_source CHECK (registration_source IN ('ONLINE', 'STAFF')),
    CONSTRAINT chk_customers_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB;

CREATE TABLE vehicles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    registration_no VARCHAR(20) NOT NULL,
    vehicle_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vehicles_registration_no (registration_no),
    KEY idx_vehicles_customer_id (customer_id),
    CONSTRAINT fk_vehicles_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT chk_vehicles_type CHECK (vehicle_type IN ('HATCHBACK', 'SEDAN', 'SUV', 'BIKE', 'OTHER'))
) ENGINE=InnoDB;

CREATE TABLE services (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    price DECIMAL(10, 2) NOT NULL,
    estimated_duration_minutes INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    KEY idx_services_active_name (active, name),
    CONSTRAINT chk_services_price CHECK (price >= 0),
    CONSTRAINT chk_services_duration CHECK (estimated_duration_minutes > 0)
) ENGINE=InnoDB;

CREATE TABLE bookings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    booking_date DATE NOT NULL,
    start_time TIME NOT NULL,
    source VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    price_snapshot DECIMAL(10, 2) NOT NULL,
    notes VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_bookings_date_status (booking_date, status),
    KEY idx_bookings_customer_date (customer_id, booking_date),
    CONSTRAINT fk_bookings_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_bookings_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT fk_bookings_service FOREIGN KEY (service_id) REFERENCES services (id),
    CONSTRAINT chk_bookings_source CHECK (source IN ('ONLINE', 'WALK_IN')),
    CONSTRAINT chk_bookings_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_bookings_price CHECK (price_snapshot >= 0)
) ENGINE=InnoDB;

CREATE TABLE shop_schedule (
    id BIGINT NOT NULL,
    opening_time TIME NOT NULL,
    closing_time TIME NOT NULL,
    slot_interval_minutes INT NOT NULL,
    capacity INT NOT NULL,
    open_days VARCHAR(100) NOT NULL,
    cancellation_cutoff_minutes INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT chk_shop_schedule_singleton CHECK (id = 1),
    CONSTRAINT chk_shop_schedule_hours CHECK (closing_time > opening_time),
    CONSTRAINT chk_shop_schedule_interval CHECK (slot_interval_minutes > 0),
    CONSTRAINT chk_shop_schedule_capacity CHECK (capacity > 0),
    CONSTRAINT chk_shop_schedule_cancellation CHECK (cancellation_cutoff_minutes >= 0)
) ENGINE=InnoDB;

INSERT INTO shop_schedule (
    id, opening_time, closing_time, slot_interval_minutes, capacity, open_days, cancellation_cutoff_minutes
) VALUES (
    1, '09:00:00', '18:00:00', 30, 2,
    'TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY', 0
);

CREATE TABLE booking_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    previous_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_booking_status_history_booking_time (booking_id, changed_at),
    CONSTRAINT fk_booking_status_history_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_booking_status_history_user FOREIGN KEY (changed_by) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE service_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    previous_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_status_history_booking_time (booking_id, changed_at),
    CONSTRAINT fk_status_history_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_status_history_user FOREIGN KEY (changed_by) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE invoices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    invoice_no VARCHAR(40) NOT NULL,
    subtotal DECIMAL(10, 2) NOT NULL,
    discount DECIMAL(10, 2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(10, 2) NOT NULL,
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoices_booking_id (booking_id),
    UNIQUE KEY uk_invoices_invoice_no (invoice_no),
    KEY idx_invoices_issued_at (issued_at),
    CONSTRAINT fk_invoices_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT chk_invoices_amounts CHECK (subtotal >= 0 AND discount >= 0 AND total_amount >= 0)
) ENGINE=InnoDB;

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PAID',
    paid_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recorded_by BIGINT NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_payments_invoice_id (invoice_id),
    UNIQUE KEY uk_payments_idempotency_key (idempotency_key),
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT fk_payments_recorder FOREIGN KEY (recorded_by) REFERENCES users (id),
    CONSTRAINT chk_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_payments_method CHECK (method IN ('CASH', 'UPI', 'CARD')),
    CONSTRAINT chk_payments_status CHECK (status IN ('PAID', 'REFUNDED'))
) ENGINE=InnoDB;

CREATE TABLE loyalty_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    booking_id BIGINT NULL,
    invoice_id BIGINT NULL,
    points_delta INT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_loyalty_customer_date (customer_id, created_at),
    UNIQUE KEY uk_loyalty_invoice_transaction (invoice_id, transaction_type),
    CONSTRAINT fk_loyalty_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_loyalty_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_loyalty_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT chk_loyalty_type CHECK (transaction_type IN ('EARN', 'REDEEM', 'ADJUST'))
) ENGINE=InnoDB;

CREATE TABLE offers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(10, 2) NOT NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NOT NULL,
    eligibility VARCHAR(500) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    KEY idx_offers_active_dates (active, starts_at, ends_at),
    CONSTRAINT chk_offers_discount_type CHECK (discount_type IN ('PERCENT', 'FIXED')),
    CONSTRAINT chk_offers_discount_value CHECK (discount_value > 0),
    CONSTRAINT chk_offers_validity CHECK (ends_at > starts_at)
) ENGINE=InnoDB;

CREATE TABLE reminders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    booking_id BIGINT NULL,
    trigger_key VARCHAR(100) NOT NULL,
    scheduled_at DATETIME NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    reminder_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    sent_at DATETIME NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reminders_customer_trigger (customer_id, trigger_key),
    KEY idx_reminders_status_schedule (status, scheduled_at),
    KEY idx_reminders_booking_id (booking_id),
    CONSTRAINT fk_reminders_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_reminders_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT chk_reminders_channel CHECK (channel IN ('IN_APP', 'EMAIL')),
    CONSTRAINT chk_reminders_status CHECK (status IN ('SCHEDULED', 'SENT', 'FAILED', 'CANCELLED'))
) ENGINE=InnoDB;

CREATE TABLE reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    booking_id BIGINT NULL,
    rating TINYINT NOT NULL,
    comment VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reviews_booking_id (booking_id),
    KEY idx_reviews_customer_date (customer_id, created_at),
    CONSTRAINT fk_reviews_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_reviews_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB;

CREATE TABLE shop_settings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    setting_key VARCHAR(100) NOT NULL,
    setting_value JSON NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shop_settings_key (setting_key)
) ENGINE=InnoDB;

INSERT INTO services (name, description, price, estimated_duration_minutes, active)
VALUES
    ('Essential Wash', 'A careful exterior clean and dry.', 399.00, 30, TRUE),
    ('Interior Refresh', 'A fresh, tidy cabin from seats to dashboard.', 799.00, 45, TRUE),
    ('Complete Care', 'An inside-and-out clean for the whole car.', 1299.00, 75, TRUE),
    ('Premium Detail', 'A deeper clean with a polished finish.', 2199.00, 120, TRUE);
