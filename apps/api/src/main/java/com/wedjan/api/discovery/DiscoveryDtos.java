package com.wedjan.api.discovery;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DiscoveryDtos {
    private DiscoveryDtos() {}

    public enum SearchSort { RELEVANCE, PRICE_ASC, PRICE_DESC, RATING, NEWEST }
    public enum EventType { WEDDING, CORPORATE, BIRTHDAY, CULTURAL, OTHER }
    public enum ShowcaseStatus { DRAFT, PUBLISHED, FLAGGED }
    public enum TagStatus { PENDING, ACCEPTED, DECLINED }
    public enum EntityType { VENDOR, SHOWCASE, PACKAGE }

    public record PackageSummary(UUID id, String title, long priceCents, String currency,
            String pricingModel, String bookingMode, int depositPct, String cancellationPolicy,
            String whatsIncludedMd, String coverUrl) {}

    public record VendorCard(UUID vendorId, String slug, String businessName, String tagline,
            String city, String country, String currency, List<String> categories,
            PackageSummary cheapestPackage, List<String> badges, String coverUrl,
            Double distanceKm, List<LocalDate> nextAvailableDates, Double relevance) {}

    public record FacetValue(String value, long count) {}
    public record SearchFacets(List<FacetValue> categories, List<FacetValue> cities,
            List<FacetValue> badges, List<FacetValue> bookingModes) {}
    public record RelaxationSuggestion(String type, String label, String value) {}
    public record VendorSearchResponse(List<VendorCard> items, SearchFacets facets,
            List<RelaxationSuggestion> suggestions, String nextCursor, long total) {}

    public record ShowcaseMediaRequest(@NotNull UUID mediaId, @Size(max = 300) String caption,
            Integer sort) {}
    public record ShowcaseVendorTagRequest(@NotNull UUID vendorId,
            @NotBlank @Size(max = 120) String roleLabel) {}
    public record ShowcaseRequest(@NotBlank @Size(max = 180) String title,
            @NotNull EventType eventType, LocalDate eventDate,
            @NotBlank @Size(max = 120) String city,
            @NotBlank @Size(min = 2, max = 2) String country,
            @NotNull UUID coverMediaId, @Size(max = 10000) String descriptionMd,
            @Size(max = 12) List<@Size(max = 40) String> styleTags,
            @NotEmpty @Size(max = 40) List<@Valid ShowcaseMediaRequest> media,
            @Size(max = 20) List<@Valid ShowcaseVendorTagRequest> vendorTags,
            ShowcaseStatus status) {}

    public record ShowcaseMedia(UUID id, UUID mediaId, String caption, int sort, String url,
            Integer width, Integer height, String blurhash) {}
    public record ShowcaseVendorTag(UUID id, UUID vendorId, String vendorSlug,
            String businessName, String roleLabel, TagStatus status) {}
    public record Showcase(UUID id, UUID ownerVendorId, String ownerVendorSlug,
            String ownerBusinessName, String title, String slug, EventType eventType,
            LocalDate eventDate, String city, String country, String descriptionMd,
            ShowcaseStatus status, List<String> styleTags, String coverUrl,
            List<ShowcaseMedia> media, List<ShowcaseVendorTag> vendorTags,
            Instant createdAt, boolean favorite) {}
    public record ShowcaseFeedResponse(List<Showcase> items, String nextCursor) {}

    public record FavoriteRequest(@NotNull EntityType entityType, @NotNull UUID entityId) {}
    public record Favorite(EntityType entityType, UUID entityId, Instant createdAt) {}
    public record FavoriteListResponse(List<Favorite> items) {}

    public record ShortlistRequest(@NotBlank @Size(max = 120) String name) {}
    public record ShortlistItemRequest(@NotNull UUID vendorId, UUID packageId,
            @Size(max = 500) String note) {}
    public record ShortlistItem(UUID id, UUID vendorId, String vendorSlug, String businessName,
            UUID packageId, String packageTitle, Long priceCents, String currency, String note) {}
    public record Shortlist(UUID id, String name, List<ShortlistItem> items, Instant createdAt) {}
    public record ShortlistListResponse(List<Shortlist> items) {}
    public record CompareResponse(List<VendorCard> items, String displayCurrency,
            Map<UUID, Long> approximatePrices, String fxNotice) {}

    public record SeoFaq(String question, String answer) {}
    public record LandingPage(String country, String city, String categorySlug,
            String categoryName, long vendorCount, Long minimumPriceCents,
            Long maximumPriceCents, Long medianPriceCents, String currency,
            List<VendorCard> vendors, List<SeoFaq> faqs,
            List<String> siblingCities, List<String> siblingCategories) {}
    public record LandingRoute(String country, String city, String category) {}
    public record LandingRouteList(List<LandingRoute> items) {}
}
