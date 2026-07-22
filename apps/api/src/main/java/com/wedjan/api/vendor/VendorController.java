package com.wedjan.api.vendor;

import com.wedjan.api.config.JwtAuthFilter;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vendors")
public class VendorController {

    private final VendorService service;

    public VendorController(VendorService service) { this.service = service; }

    @GetMapping("/{slug}")
    public VendorDtos.VendorPublicResponse publicProfile(@PathVariable String slug) {
        return service.publicProfile(slug);
    }

    @GetMapping("/{slug}/packages")
    public List<VendorDtos.PackageDto> publicPackages(@PathVariable String slug) {
        return service.publicPackages(slug);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('VENDOR')")
    public VendorDtos.VendorMeResponse me() { return service.me(accountId()); }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('VENDOR')")
    public VendorDtos.VendorMeResponse updateProfile(
            @Valid @RequestBody VendorDtos.VendorProfileUpdateRequest request) {
        return service.updateProfile(accountId(), request);
    }

    @PutMapping("/me/categories")
    @PreAuthorize("hasRole('VENDOR')")
    public VendorDtos.VendorMeResponse setCategories(
            @Valid @RequestBody VendorDtos.VendorCategoriesRequest request) {
        return service.setCategories(accountId(), request);
    }

    @PostMapping("/me/service-areas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VENDOR')")
    public VendorDtos.ServiceArea addArea(@Valid @RequestBody VendorDtos.ServiceAreaRequest request) {
        return service.addServiceArea(accountId(), request);
    }

    @PatchMapping("/me/service-areas/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public VendorDtos.ServiceArea updateArea(@PathVariable UUID id,
            @Valid @RequestBody VendorDtos.ServiceAreaRequest request) {
        return service.updateServiceArea(accountId(), id, request);
    }

    @DeleteMapping("/me/service-areas/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Void> deleteArea(@PathVariable UUID id) {
        service.deleteServiceArea(accountId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/packages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VENDOR')")
    public VendorDtos.PackageDto createPackage(@Valid @RequestBody VendorDtos.PackageRequest request) {
        return service.createPackage(accountId(), request);
    }

    @PatchMapping("/me/packages/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public VendorDtos.PackageDto updatePackage(@PathVariable UUID id,
            @Valid @RequestBody VendorDtos.PackageRequest request) {
        return service.updatePackage(accountId(), id, request);
    }

    @DeleteMapping("/me/packages/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Void> archivePackage(@PathVariable UUID id) {
        service.archivePackage(accountId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/packages/{id}/publish")
    @PreAuthorize("hasRole('VENDOR')")
    public VendorDtos.PackageDto publishPackage(@PathVariable UUID id) {
        return service.publishPackage(accountId(), id);
    }

    @PostMapping("/me/packages/{id}/unpublish")
    @PreAuthorize("hasRole('VENDOR')")
    public VendorDtos.PackageDto unpublishPackage(@PathVariable UUID id) {
        return service.unpublishPackage(accountId(), id);
    }

    @PostMapping("/me/add-ons")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VENDOR')")
    public VendorDtos.AddOn createAddOn(@Valid @RequestBody VendorDtos.AddOnRequest request) {
        return service.createAddOn(accountId(), request);
    }

    @PatchMapping("/me/add-ons/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public VendorDtos.AddOn updateAddOn(@PathVariable UUID id,
            @Valid @RequestBody VendorDtos.AddOnRequest request) {
        return service.updateAddOn(accountId(), id, request);
    }

    @DeleteMapping("/me/add-ons/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<Void> deleteAddOn(@PathVariable UUID id) {
        service.deleteAddOn(accountId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/media")
    @PreAuthorize("hasRole('VENDOR')")
    public VendorDtos.VendorMeResponse setMedia(@Valid @RequestBody VendorDtos.VendorMediaRequest request) {
        return service.setMedia(accountId(), request);
    }

    @PutMapping("/me/faqs")
    @PreAuthorize("hasRole('VENDOR')")
    public VendorDtos.VendorMeResponse setFaqs(@Valid @RequestBody VendorDtos.VendorFaqsRequest request) {
        return service.setFaqs(accountId(), request);
    }

    @PostMapping("/me/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('VENDOR')")
    public VendorDtos.VerificationDocument uploadDocument(
            @Valid @RequestBody VendorDtos.VerificationDocumentRequest request) {
        return service.uploadDocument(accountId(), request);
    }

    @PostMapping("/me/submit")
    @PreAuthorize("hasRole('VENDOR')")
    public VendorDtos.VendorMeResponse submit() { return service.submit(accountId()); }

    private static UUID accountId() { return JwtAuthFilter.currentAccountId(); }
}
