package com.wedjan.api.auth;

import com.wedjan.api.common.Uuidv7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Rotating refresh token. Rows form a "family" (one per login/device);
 * rotation creates a new row in the same family. Presenting an
 * already-rotated token is reuse → the whole family is revoked.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "device_name")
    private String deviceName;

    private String ip;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "rotated_from")
    private UUID rotatedFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static RefreshToken create(
            UUID accountId, UUID familyId, String tokenHash, String deviceName, String ip,
            Instant expiresAt, UUID rotatedFrom) {
        RefreshToken token = new RefreshToken();
        token.id = Uuidv7.next();
        token.accountId = accountId;
        token.familyId = familyId;
        token.tokenHash = tokenHash;
        token.deviceName = deviceName;
        token.ip = ip;
        token.expiresAt = expiresAt;
        token.rotatedFrom = rotatedFrom;
        return token;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public UUID getFamilyId() { return familyId; }
    public String getTokenHash() { return tokenHash; }
    public String getDeviceName() { return deviceName; }
    public String getIp() { return ip; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
