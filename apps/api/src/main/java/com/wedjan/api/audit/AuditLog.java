package com.wedjan.api.audit;

import com.wedjan.api.common.Uuidv7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    private UUID id;

    @Column(name = "actor_account_id")
    private UUID actorAccountId;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AuditLog of(UUID actorAccountId, String action, String entityType, String entityId,
            Map<String, Object> metadata) {
        AuditLog log = new AuditLog();
        log.id = Uuidv7.next();
        log.actorAccountId = actorAccountId;
        log.action = action;
        log.entityType = entityType;
        log.entityId = entityId;
        log.metadata = metadata;
        return log;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
