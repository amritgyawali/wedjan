package com.wedjan.api.vendor;

import com.wedjan.api.config.JwtAuthFilter;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/verifications")
@PreAuthorize("hasRole('ADMIN')")
public class VerificationAdminController {

    private final VendorService service;

    public VerificationAdminController(VendorService service) { this.service = service; }

    @GetMapping
    public VendorDtos.VerificationQueueResponse queue(
            @RequestParam(required = false) VendorDtos.DocumentStatus status,
            @RequestParam(defaultValue = "20") int limit) {
        return service.verificationQueue(status, limit);
    }

    @PostMapping("/{id}/approve")
    public VendorDtos.VerificationDocument approve(@PathVariable UUID id,
            @Valid @RequestBody(required = false) VendorDtos.VerificationReviewRequest request) {
        return service.review(JwtAuthFilter.currentAccountId(), id, true, request);
    }

    @PostMapping("/{id}/reject")
    public VendorDtos.VerificationDocument reject(@PathVariable UUID id,
            @Valid @RequestBody VendorDtos.VerificationReviewRequest request) {
        return service.review(JwtAuthFilter.currentAccountId(), id, false, request);
    }
}
