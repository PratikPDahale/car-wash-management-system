package com.washcare.crm.customer;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

import com.washcare.crm.auth.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "customers", uniqueConstraints = @UniqueConstraint(name = "uk_customers_user_id", columnNames = "user_id"))
public class Customer {

    private static final Pattern PHONE_FORMATTING = Pattern.compile("[\\s().-]");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_source", nullable = false, length = 20)
    private RegistrationSource registrationSource;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Customer() {
    }

    private Customer(UserAccount user, String name, String phone, String email, String notes, RegistrationSource source) {
        this.user = user;
        this.name = name;
        this.phone = normalizePhone(phone);
        this.email = normalizeEmail(email);
        this.notes = notes;
        this.registrationSource = source;
        this.status = "ACTIVE";
    }

    public static Customer onlineRegistration(UserAccount user, String name, String phone, String email) {
        return new Customer(user, name, phone, email, null, RegistrationSource.ONLINE);
    }

    public static Customer staffCreated(String name, String phone, String email, String notes) {
        return new Customer(null, name, phone, email, notes, RegistrationSource.STAFF);
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizePhone(String phone) {
        return PHONE_FORMATTING.matcher(phone.trim()).replaceAll("");
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getNotes() {
        return notes;
    }

    public RegistrationSource getRegistrationSource() {
        return registrationSource;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public void update(String name, String phone, String email, String notes) {
        this.name = name.trim();
        this.phone = normalizePhone(phone);
        this.email = normalizeEmail(email);
        this.notes = notes;
    }

    public enum RegistrationSource {
        ONLINE,
        STAFF
    }
}
