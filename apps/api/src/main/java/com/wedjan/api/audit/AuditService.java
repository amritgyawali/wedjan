package com.wedjan.api.audit;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only audit trail, written asynchronously in its own transaction so
 * audit writes never block or roll back the business operation.
 * No PII in metadata — ids and enums only.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorAccountId, String action, String entityType, String entityId,
            Map<String, Object> metadata) {
        try {
            repository.save(AuditLog.of(actorAccountId, action, entityType, entityId, metadata));
        } catch (Exception e) {
            log.error("Failed to write audit log action={} entity={}:{}", action, entityType, entityId, e);
        }
    }
}
