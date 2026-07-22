package com.wedjan.api.vendor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class VendorDtos {

    private VendorDtos() {}

    public enum VendorStatus { DRAFT, SUBMITTED, UNDER_REVIEW, VERIFIED, REJECTED, SUSPENDED }
    public enum PricingModel { FLAT, PER_HOUR, PER_GUEST, STARTING_AT }
    public enum BookingMode { INSTANT, REQUEST }
    public enum CancellationPolicy { FLEXIBLE, MODERATE, STRICT }
    public enum PackageStatus { DRAFT, PUBLISHED, ARCHIVED }
    public enum MediaKind { GALLERY, COVER, LOGO, SHOWREEL }
    public enum DocumentType { BUSINESS_REGISTRATION, GOVERNMENT_ID, INSURANCE, PORTFOLIO_PROOF }
    public enum DocumentStatus { PENDING, APPROVED, REJECTED }

    public record Category(UUID id, String slug, String name, UUID parentId, String icon,
            int sort, boolean isActive) {}
    public record CategoryListResponse(List<Category> items) {}

    public record VendorProfile(UUID accountId, String businessName, String slug, String tagline,
            String about, Integer foundedYear, Integer teamSize, List<String> languages,
            String baseCity, String baseCountry, Double lat, Double lng, String website,
            String instagram, VendorStatus status, String rejectionReason, int onboardingStep,
            String currency, String availabilityMode, String timezone) {}

    public record VendorProfileUpdateRequest(
            @Size(max = 160) String businessName,
            @Size(max = 160) String tagline,
            @Size(max = 5000) String about,
            @Min(1900) @Max(2100) Integer foundedYear,
            @Min(1) @Max(10000) Integer teamSize,
            @Size(max = 10) List<@Size(max = 40) String> languages,
            @Size(max = 120) String baseCity,
            @Size(min = 2, max = 2) String baseCountry,
            @Min(-90) @Max(90) Double lat,
            @Min(-180) @Max(180) Double lng,
            @Size(max = 300) String website,
            @Size(max = 100) String instagram,
            String currency,
            @Min(1) @Max(7) Integer onboardingStep) {}

    public record VendorCategorySelection(@NotNull UUID categoryId, boolean isPrimary) {}
    public record VendorCategoriesRequest(
            @NotEmpty @Size(max = 4) List<@Valid VendorCategorySelection> items) {}

    public record ServiceAreaRequest(
            @NotBlank String mode,
            @NotBlank @Size(max = 120) String city,
            @NotBlank @Size(min = 2, max = 2) String country,
            Double lat,
            Double lng,
            @Min(1) @Max(1000) Integer radiusKm,
            @Min(0) Long travelFeeCents,
            @Size(max = 300) String travelFeeNote) {}
    public record ServiceArea(UUID id, String mode, String city, String country, Double lat,
            Double lng, Integer radiusKm, Long travelFeeCents, String travelFeeNote) {}

    public record PackageRequest(
            @NotBlank @Size(min = 3, max = 160) String title,
            @NotNull UUID categoryId,
            @Size(max = 10000) String descriptionMd,
            @Min(1) long priceCents,
            @NotNull PricingModel pricingModel,
            @Min(1) Integer minGuests,
            @Min(1) Integer maxGuests,
            @Min(15) Integer durationMinutes,
            @Size(max = 10000) String whatsIncludedMd,
            @Size(max = 10000) String whatsExcludedMd,
            BookingMode bookingMode,
            @Min(10) @Max(100) Integer depositPct,
            CancellationPolicy cancellationPolicy,
            Boolean allowSameDay,
            UUID coverMediaId,
            Integer sort) {}

    public record PackageDto(UUID id, UUID vendorId, UUID categoryId, String title, String slug,
            String descriptionMd, long priceCents, String currency, PricingModel pricingModel,
            Integer minGuests, Integer maxGuests, Integer durationMinutes,
            String whatsIncludedMd, String whatsExcludedMd, BookingMode bookingMode,
            int depositPct, CancellationPolicy cancellationPolicy, PackageStatus status,
            boolean allowSameDay, long version, UUID coverMediaId, String coverUrl, int sort) {}

    public record AddOnRequest(UUID packageId,
            @NotBlank @Size(min = 2, max = 160) String title,
            @Min(1) long priceCents,
            @NotNull PricingModel pricingModel,
            @Min(1) @Max(999) Integer maxQty,
            @Size(max = 1000) String description) {}
    public record AddOn(UUID id, UUID packageId, String title, long priceCents,
            PricingModel pricingModel, Integer maxQty, String description) {}

    public record VendorMediaItemRequest(@NotNull UUID mediaId, @NotNull MediaKind kind,
            @Size(max = 300) String caption, Integer sort) {}
    public record VendorMediaRequest(
            @Size(max = 60) List<@Valid VendorMediaItemRequest> items) {}
    public record VendorMediaItem(UUID id, UUID mediaId, MediaKind kind, String caption, int sort,
            String url, Integer width, Integer height, String blurhash) {}

    public record VendorFaqItem(
            @NotBlank @Size(min = 5, max = 300) String question,
            @NotBlank @Size(min = 5, max = 3000) String answerMd) {}
    public record VendorFaqsRequest(@Size(max = 12) List<@Valid VendorFaqItem> items) {}

    public record VerificationDocumentRequest(@NotNull DocumentType type, @NotNull UUID mediaId) {}
    public record VerificationDocument(UUID id, DocumentType type, DocumentStatus status,
            String reviewerNote, Instant reviewedAt, Instant createdAt) {}
    public record VendorBadge(String badge, Instant grantedAt, Instant expiresAt) {}
    public record SubmitGate(String key, int step, boolean passed, String message) {}

    public record VendorMeResponse(VendorProfile profile,
            List<VendorCategorySelection> categories, List<ServiceArea> serviceAreas,
            List<PackageDto> packages, List<AddOn> addOns, List<VendorMediaItem> media,
            List<VendorFaqItem> faqs, List<VerificationDocument> documents,
            List<VendorBadge> badges, List<SubmitGate> gates, int listingStrength) {}

    public record VendorPublicResponse(String slug, String businessName, String tagline,
            String about, Integer foundedYear, Integer teamSize, List<String> languages,
            String baseCity, String baseCountry, String website, String instagram, String currency,
            List<Category> categories, List<PackageDto> packages, List<AddOn> addOns,
            List<VendorMediaItem> media, List<VendorFaqItem> faqs, List<VendorBadge> badges,
            List<ServiceArea> serviceAreas) {}

    public record VerificationQueueItem(VerificationDocument document, UUID vendorAccountId,
            String businessName, VendorStatus vendorStatus, String mediaUrl) {}
    public record VerificationQueueResponse(List<VerificationQueueItem> items, String nextCursor) {}
    public record VerificationReviewRequest(@Size(max = 1000) String note, Instant expiresAt) {}
}
