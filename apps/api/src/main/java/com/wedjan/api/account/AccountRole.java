package com.wedjan.api.account;

import com.wedjan.api.common.Uuidv7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_roles")
public class AccountRole {

    public enum Role { CUSTOMER, VENDOR, FREELANCER, ADMIN }

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static AccountRole grant(UUID accountId, Role role) {
        AccountRole accountRole = new AccountRole();
        accountRole.id = Uuidv7.next();
        accountRole.accountId = accountId;
        accountRole.role = role;
        accountRole.grantedAt = Instant.now();
        return accountRole;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public Role getRole() { return role; }
    public Instant getGrantedAt() { return grantedAt; }
}
