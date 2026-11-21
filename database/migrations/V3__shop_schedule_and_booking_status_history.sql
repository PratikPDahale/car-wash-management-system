USE washcare_crm;

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
