package com.sltours.accounts.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
public class Account {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable = false, unique = true, length = 180) private String email;
    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false, length = 100) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Role role;
    @Column(nullable = false) private boolean enabled = true;
    @Column(nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime lastLoginAt;
    @Column(length = 40) private String phone;
    private OffsetDateTime emailVerifiedAt;
    public enum Role { ADMIN, STAFF, CUSTOMER, DRIVER, PARTNER }
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String value) { email = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String value) { passwordHash = value; }
    public Role getRole() { return role; }
    public void setRole(Role value) { role = value; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getPhone() { return phone; }
    public void setPhone(String value) { phone = value; }
    public OffsetDateTime getEmailVerifiedAt() { return emailVerifiedAt; }
    public void setEmailVerifiedAt(OffsetDateTime value) { emailVerifiedAt = value; }
    public boolean isEmailVerified() { return emailVerifiedAt != null; }
    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(OffsetDateTime value) { lastLoginAt = value; }
}
