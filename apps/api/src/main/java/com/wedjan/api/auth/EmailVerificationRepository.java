package com.wedjan.api.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {

    Optional<EmailVerification> findTopByAccountIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            UUID accountId, EmailVerification.Purpose purpose);
}
