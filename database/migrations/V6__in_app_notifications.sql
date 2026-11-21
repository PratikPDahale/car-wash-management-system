USE washcare_crm;

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
