package com.wedjan.api.vendor;

import com.wedjan.api.account.AccountRepository;
import com.wedjan.api.audit.AuditService;
import com.wedjan.api.auth.MailService;
import com.wedjan.api.common.ApiException;
import com.wedjan.api.common.ErrorResponse;
import com.wedjan.api.common.Uuidv7;
import com.wedjan.api.config.WedjanProperties;
import com.wedjan.api.media.MediaAsset;
import com.wedjan.api.media.MediaAssetRepository;
import com.wedjan.api.vendor.VendorDtos.AddOn;
import com.wedjan.api.vendor.VendorDtos.AddOnRequest;
import com.wedjan.api.vendor.VendorDtos.Category;
import com.wedjan.api.vendor.VendorDtos.DocumentStatus;
import com.wedjan.api.vendor.VendorDtos.DocumentType;
import com.wedjan.api.vendor.VendorDtos.MediaKind;
import com.wedjan.api.vendor.VendorDtos.PackageDto;
import com.wedjan.api.vendor.VendorDtos.PackageRequest;
import com.wedjan.api.vendor.VendorDtos.PackageStatus;
import com.wedjan.api.vendor.VendorDtos.ServiceArea;
import com.wedjan.api.vendor.VendorDtos.ServiceAreaRequest;
import com.wedjan.api.vendor.VendorDtos.SubmitGate;
import com.wedjan.api.vendor.VendorDtos.VendorBadge;
import com.wedjan.api.vendor.VendorDtos.VendorCategorySelection;
import com.wedjan.api.vendor.VendorDtos.VendorFaqItem;
import com.wedjan.api.vendor.VendorDtos.VendorMediaItem;
import com.wedjan.api.vendor.VendorDtos.VendorMediaItemRequest;
import com.wedjan.api.vendor.VendorDtos.VendorProfile;
import com.wedjan.api.vendor.VendorDtos.VendorStatus;
import com.wedjan.api.vendor.VendorDtos.VerificationDocument;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorService {

    private static final Set<String> CURRENCIES = Set.of("AUD", "USD", "GBP", "NPR");
    private static final Set<String> AREA_MODES = Set.of("CITY_RADIUS", "REGION");

    private final JdbcTemplate jdbc;
    private final MediaAssetRepository mediaRepository;
    private final WedjanProperties properties;
    private final AccountRepository accountRepository;
    private final MailService mailService;
    private final AuditService auditService;

    public VendorService(JdbcTemplate jdbc, MediaAssetRepository mediaRepository,
            WedjanProperties properties, AccountRepository accountRepository,
            MailService mailService, AuditService auditService) {
        this.jdbc = jdbc;
        this.mediaRepository = mediaRepository;
        this.properties = properties;
        this.accountRepository = accountRepository;
        this.mailService = mailService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Category> categories() {
        return jdbc.query("""
                SELECT id, slug, name, parent_id, icon, sort, is_active
                FROM categories WHERE is_active AND deleted_at IS NULL
                ORDER BY parent_id NULLS FIRST, sort, name
                """, this::mapCategory);
    }

    @Transactional
    public VendorDtos.VendorMeResponse me(UUID accountId) {
        ensureProfile(accountId);
        return workspace(accountId);
    }

    @Transactional
    public VendorDtos.VendorMeResponse updateProfile(UUID accountId,
            VendorDtos.VendorProfileUpdateRequest request) {
        ensureProfile(accountId);
        VendorProfile current = profile(accountId, false);
        String currency = normalizeCurrency(request.currency());
        if (currency != null && !currency.equals(current.currency()) && hasPublishedPackage(accountId)) {
            throw ApiException.conflict("VENDOR_CURRENCY_LOCKED",
                    "Currency cannot change after a package is published");
        }

        String businessName = clean(request.businessName());
        String slug = current.slug();
        if (slug == null && businessName != null) {
            slug = uniqueSlug(businessName);
        }
        boolean sensitiveEdit = current.status() == VendorStatus.VERIFIED && businessName != null
                && !businessName.equals(current.businessName());

        jdbc.update("""
                UPDATE vendor_profiles SET
                    business_name=COALESCE(?,business_name), slug=COALESCE(?,slug),
                    tagline=COALESCE(?,tagline), about=COALESCE(?,about),
                    founded_year=COALESCE(?,founded_year), team_size=COALESCE(?,team_size),
                    languages=COALESCE(?,languages), base_city=COALESCE(?,base_city),
                    base_country=COALESCE(?,base_country), lat=COALESCE(?,lat), lng=COALESCE(?,lng),
                    website=COALESCE(?,website), instagram=COALESCE(?,instagram),
                    currency=COALESCE(?,currency), onboarding_step=GREATEST(onboarding_step,COALESCE(?,onboarding_step)),
                    status=CASE WHEN ? THEN 'UNDER_REVIEW' ELSE status END,
                    rejection_reason=CASE WHEN ? THEN NULL ELSE rejection_reason END,
                    version=version+1, updated_at=now()
                WHERE account_id=?
                """, businessName, slug, clean(request.tagline()), clean(request.about()),
                request.foundedYear(), request.teamSize(), sqlArray(request.languages()),
                clean(request.baseCity()), upper(request.baseCountry()), request.lat(), request.lng(),
                clean(request.website()), clean(request.instagram()), currency, request.onboardingStep(),
                sensitiveEdit, sensitiveEdit, accountId);
        return workspace(accountId);
    }

    @Transactional
    public VendorDtos.VendorMeResponse setCategories(UUID accountId,
            VendorDtos.VendorCategoriesRequest request) {
        ensureProfile(accountId);
        long primary = request.items().stream().filter(VendorCategorySelection::isPrimary).count();
        Set<UUID> ids = new HashSet<>();
        if (primary != 1 || request.items().stream().anyMatch(i -> !ids.add(i.categoryId()))) {
            throw ApiException.badRequest("VENDOR_CATEGORIES_INVALID",
                    "Choose one primary category and no duplicate categories");
        }
        for (var item : request.items()) requireCategory(item.categoryId());
        jdbc.update("DELETE FROM vendor_categories WHERE vendor_id=?", accountId);
        for (var item : request.items()) {
            jdbc.update("""
                    INSERT INTO vendor_categories(id,vendor_id,category_id,is_primary,created_by)
                    VALUES (?,?,?,?,?)
                    """, Uuidv7.next(), accountId, item.categoryId(), item.isPrimary(), accountId);
        }
        advance(accountId, 2);
        return workspace(accountId);
    }

    @Transactional
    public ServiceArea addServiceArea(UUID accountId, ServiceAreaRequest request) {
        ensureProfile(accountId);
        validateArea(request);
        UUID id = Uuidv7.next();
        jdbc.update("""
                INSERT INTO service_areas(id,vendor_id,mode,city,country,lat,lng,radius_km,
                    travel_fee_cents,travel_fee_note,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, id, accountId, request.mode(), request.city().trim(), upper(request.country()),
                request.lat(), request.lng(), request.radiusKm(), request.travelFeeCents(),
                clean(request.travelFeeNote()), accountId);
        advance(accountId, 3);
        return serviceArea(accountId, id);
    }

    @Transactional
    public ServiceArea updateServiceArea(UUID accountId, UUID areaId, ServiceAreaRequest request) {
        validateArea(request);
        int count = jdbc.update("""
                UPDATE service_areas SET mode=?,city=?,country=?,lat=?,lng=?,radius_km=?,
                    travel_fee_cents=?,travel_fee_note=?,updated_at=now()
                WHERE id=? AND vendor_id=? AND deleted_at IS NULL
                """, request.mode(), request.city().trim(), upper(request.country()), request.lat(),
                request.lng(), request.radiusKm(), request.travelFeeCents(), clean(request.travelFeeNote()),
                areaId, accountId);
        if (count == 0) throw notFound("Service area");
        return serviceArea(accountId, areaId);
    }

    @Transactional
    public void deleteServiceArea(UUID accountId, UUID areaId) {
        if (jdbc.update("UPDATE service_areas SET deleted_at=now() WHERE id=? AND vendor_id=? AND deleted_at IS NULL",
                areaId, accountId) == 0) throw notFound("Service area");
    }

    @Transactional
    public PackageDto createPackage(UUID accountId, PackageRequest request) {
        ensureProfile(accountId);
        validatePackage(accountId, request, false);
        UUID id = Uuidv7.next();
        String slug = uniquePackageSlug(accountId, request.title(), null);
        String currency = profile(accountId, false).currency();
        jdbc.update("""
                INSERT INTO packages(id,vendor_id,category_id,title,slug,description_md,price_cents,
                    currency,pricing_model,min_guests,max_guests,duration_minutes,whats_included_md,
                    whats_excluded_md,booking_mode,deposit_pct,cancellation_policy,cover_media_id,sort,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, accountId, request.categoryId(), request.title().trim(), slug,
                clean(request.descriptionMd()), request.priceCents(), currency, request.pricingModel().name(),
                request.minGuests(), request.maxGuests(), request.durationMinutes(),
                clean(request.whatsIncludedMd()), clean(request.whatsExcludedMd()),
                value(request.bookingMode(), VendorDtos.BookingMode.REQUEST).name(),
                value(request.depositPct(), 25),
                value(request.cancellationPolicy(), VendorDtos.CancellationPolicy.MODERATE).name(),
                request.coverMediaId(), value(request.sort(), 0), accountId);
        advance(accountId, 4);
        return packageById(accountId, id);
    }

    @Transactional
    public PackageDto updatePackage(UUID accountId, UUID packageId, PackageRequest request) {
        PackageDto current = packageById(accountId, packageId);
        boolean published = current.status() == PackageStatus.PUBLISHED;
        validatePackage(accountId, request, published);
        if (published) {
            jdbc.update("""
                    INSERT INTO package_price_versions(id,package_id,version,price_cents,currency,
                        pricing_model,deposit_pct,cancellation_policy,booking_mode)
                    SELECT ?,id,version,price_cents,currency,pricing_model,deposit_pct,cancellation_policy,booking_mode
                    FROM packages WHERE id=?
                    ON CONFLICT (package_id,version) DO NOTHING
                    """, Uuidv7.next(), packageId);
        }
        int count = jdbc.update("""
                UPDATE packages SET category_id=?,title=?,description_md=?,price_cents=?,pricing_model=?,
                    min_guests=?,max_guests=?,duration_minutes=?,whats_included_md=?,whats_excluded_md=?,
                    booking_mode=?,deposit_pct=?,cancellation_policy=?,cover_media_id=?,sort=?,
                    version=version+1,updated_at=now()
                WHERE id=? AND vendor_id=? AND deleted_at IS NULL AND status<>'ARCHIVED'
                """, request.categoryId(), request.title().trim(), clean(request.descriptionMd()),
                request.priceCents(), request.pricingModel().name(), request.minGuests(), request.maxGuests(),
                request.durationMinutes(), clean(request.whatsIncludedMd()), clean(request.whatsExcludedMd()),
                value(request.bookingMode(), VendorDtos.BookingMode.REQUEST).name(),
                value(request.depositPct(), 25),
                value(request.cancellationPolicy(), VendorDtos.CancellationPolicy.MODERATE).name(),
                request.coverMediaId(), value(request.sort(), 0), packageId, accountId);
        if (count == 0) throw notFound("Package");
        return packageById(accountId, packageId);
    }

    @Transactional
    public void archivePackage(UUID accountId, UUID packageId) {
        if (jdbc.update("""
                UPDATE packages SET status='ARCHIVED',deleted_at=now(),updated_at=now()
                WHERE id=? AND vendor_id=? AND deleted_at IS NULL
                """, packageId, accountId) == 0) throw notFound("Package");
    }

    @Transactional
    public PackageDto publishPackage(UUID accountId, UUID packageId) {
        PackageDto item = packageById(accountId, packageId);
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        if (item.priceCents() <= 0) errors.add(field("priceCents", "Price must be greater than zero"));
        if (includedCount(item.whatsIncludedMd()) < 3) {
            errors.add(field("whatsIncludedMd", "List at least 3 included items, one per line"));
        }
        if (item.coverMediaId() == null) errors.add(field("coverMediaId", "Add a package photo"));
        if (!errors.isEmpty()) {
            throw ApiException.unprocessable("PACKAGE_PUBLISH_BLOCKED", "Package is not ready to publish", errors);
        }
        requireMedia(accountId, item.coverMediaId(), Set.of(MediaAsset.Kind.IMAGE));
        jdbc.update("UPDATE packages SET status='PUBLISHED',updated_at=now() WHERE id=? AND vendor_id=?",
                packageId, accountId);
        return packageById(accountId, packageId);
    }

    @Transactional
    public PackageDto unpublishPackage(UUID accountId, UUID packageId) {
        if (jdbc.update("""
                UPDATE packages SET status='DRAFT',updated_at=now()
                WHERE id=? AND vendor_id=? AND deleted_at IS NULL AND status<>'ARCHIVED'
                """, packageId, accountId) == 0) throw notFound("Package");
        return packageById(accountId, packageId);
    }

    @Transactional
    public AddOn createAddOn(UUID accountId, AddOnRequest request) {
        ensureProfile(accountId);
        validateAddOn(accountId, request);
        UUID id = Uuidv7.next();
        jdbc.update("""
                INSERT INTO add_ons(id,vendor_id,package_id,title,price_cents,pricing_model,max_qty,description,created_by)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, id, accountId, request.packageId(), request.title().trim(), request.priceCents(),
                request.pricingModel().name(), request.maxQty(), clean(request.description()), accountId);
        return addOn(accountId, id);
    }

    @Transactional
    public AddOn updateAddOn(UUID accountId, UUID addOnId, AddOnRequest request) {
        validateAddOn(accountId, request);
        if (jdbc.update("""
                UPDATE add_ons SET package_id=?,title=?,price_cents=?,pricing_model=?,max_qty=?,
                    description=?,updated_at=now() WHERE id=? AND vendor_id=? AND deleted_at IS NULL
                """, request.packageId(), request.title().trim(), request.priceCents(),
                request.pricingModel().name(), request.maxQty(), clean(request.description()),
                addOnId, accountId) == 0) throw notFound("Add-on");
        return addOn(accountId, addOnId);
    }

    @Transactional
    public void deleteAddOn(UUID accountId, UUID addOnId) {
        if (jdbc.update("UPDATE add_ons SET deleted_at=now() WHERE id=? AND vendor_id=? AND deleted_at IS NULL",
                addOnId, accountId) == 0) throw notFound("Add-on");
    }

    @Transactional
    public VendorDtos.VendorMeResponse setMedia(UUID accountId, VendorDtos.VendorMediaRequest request) {
        ensureProfile(accountId);
        List<VendorMediaItemRequest> items = request.items() == null ? List.of() : request.items();
        if (items.stream().filter(i -> i.kind() == MediaKind.COVER).count() > 1
                || items.stream().filter(i -> i.kind() == MediaKind.LOGO).count() > 1) {
            throw ApiException.badRequest("VENDOR_MEDIA_INVALID", "Choose at most one cover and one logo");
        }
        Set<UUID> seen = new HashSet<>();
        for (var item : items) {
            if (!seen.add(item.mediaId())) {
                throw ApiException.badRequest("VENDOR_MEDIA_DUPLICATE", "Each media item can be used once");
            }
            Set<MediaAsset.Kind> kinds = item.kind() == MediaKind.SHOWREEL
                    ? Set.of(MediaAsset.Kind.VIDEO) : Set.of(MediaAsset.Kind.IMAGE);
            requireMedia(accountId, item.mediaId(), kinds);
        }
        jdbc.update("DELETE FROM vendor_media WHERE vendor_id=?", accountId);
        int index = 0;
        for (var item : items) {
            jdbc.update("""
                    INSERT INTO vendor_media(id,vendor_id,media_id,kind,caption,sort,created_by)
                    VALUES (?,?,?,?,?,?,?)
                    """, Uuidv7.next(), accountId, item.mediaId(), item.kind().name(),
                    clean(item.caption()), item.sort() == null ? index : item.sort(), accountId);
            index++;
        }
        advance(accountId, 5);
        return workspace(accountId);
    }

    @Transactional
    public VendorDtos.VendorMeResponse setFaqs(UUID accountId, VendorDtos.VendorFaqsRequest request) {
        ensureProfile(accountId);
        List<VendorFaqItem> items = request.items() == null ? List.of() : request.items();
        jdbc.update("DELETE FROM vendor_faqs WHERE vendor_id=?", accountId);
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            jdbc.update("""
                    INSERT INTO vendor_faqs(id,vendor_id,question,answer_md,sort,created_by)
                    VALUES (?,?,?,?,?,?)
                    """, Uuidv7.next(), accountId, item.question().trim(), item.answerMd().trim(), i, accountId);
        }
        return workspace(accountId);
    }

    @Transactional
    public VerificationDocument uploadDocument(UUID accountId,
            VendorDtos.VerificationDocumentRequest request) {
        ensureProfile(accountId);
        requireMedia(accountId, request.mediaId(), Set.of(MediaAsset.Kind.IMAGE, MediaAsset.Kind.DOC));
        jdbc.update("""
                UPDATE verification_documents SET deleted_at=now(),updated_at=now()
                WHERE vendor_id=? AND type=? AND status IN ('PENDING','REJECTED') AND deleted_at IS NULL
                """, accountId, request.type().name());
        UUID id = Uuidv7.next();
        jdbc.update("""
                INSERT INTO verification_documents(id,vendor_id,type,media_id,status,created_by)
                VALUES (?,?,?,?, 'PENDING', ?)
                """, id, accountId, request.type().name(), request.mediaId(), accountId);
        advance(accountId, 6);
        return document(id);
    }

    @Transactional
    public VendorDtos.VendorMeResponse submit(UUID accountId) {
        ensureProfile(accountId);
        List<SubmitGate> gates = gates(accountId);
        List<ErrorResponse.FieldError> errors = gates.stream().filter(g -> !g.passed())
                .map(g -> field("step" + g.step() + "." + g.key(), g.message())).toList();
        if (!errors.isEmpty()) {
            throw ApiException.unprocessable("VENDOR_SUBMIT_BLOCKED",
                    "Complete every onboarding requirement before submitting", errors);
        }
        jdbc.update("""
                UPDATE vendor_profiles SET status='SUBMITTED',rejection_reason=NULL,
                    onboarding_step=7,updated_at=now() WHERE account_id=?
                """, accountId);
        auditService.record(accountId, "vendor.submitted", "VendorProfile", accountId.toString(), null);
        return workspace(accountId);
    }

    @Transactional(readOnly = true)
    public VendorDtos.VendorPublicResponse publicProfile(String slug) {
        UUID vendorId;
        try {
            vendorId = jdbc.queryForObject("""
                    SELECT account_id FROM vendor_profiles
                    WHERE slug=? AND is_public AND status<>'SUSPENDED' AND deleted_at IS NULL
                    """, UUID.class, slug);
        } catch (EmptyResultDataAccessException ex) {
            throw notFound("Vendor");
        }
        VendorProfile p = profile(vendorId, true);
        return new VendorDtos.VendorPublicResponse(p.slug(), p.businessName(), p.tagline(), p.about(),
                p.foundedYear(), p.teamSize(), p.languages(), p.baseCity(), p.baseCountry(), p.website(),
                p.instagram(), p.currency(), selectedCategories(vendorId), publicPackages(vendorId),
                addOns(vendorId), media(vendorId), faqs(vendorId), badges(vendorId), serviceAreas(vendorId));
    }

    @Transactional(readOnly = true)
    public List<PackageDto> publicPackages(String slug) {
        return publicProfile(slug).packages();
    }

    @Transactional(readOnly = true)
    public VendorDtos.VerificationQueueResponse verificationQueue(DocumentStatus status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String where = status == null ? "" : " AND d.status=?";
        List<Object> params = new ArrayList<>();
        if (status != null) params.add(status.name());
        params.add(safeLimit);
        List<VendorDtos.VerificationQueueItem> items = jdbc.query("""
                SELECT d.id,d.type,d.status,d.reviewer_note,d.reviewed_at,d.created_at,d.vendor_id,
                    v.business_name,v.status vendor_status,m.storage_key
                FROM verification_documents d
                JOIN vendor_profiles v ON v.account_id=d.vendor_id
                JOIN media_assets m ON m.id=d.media_id
                WHERE d.deleted_at IS NULL
                """ + where + " ORDER BY d.created_at LIMIT ?", (rs, rowNum) ->
                new VendorDtos.VerificationQueueItem(mapDocument(rs, rowNum), rs.getObject("vendor_id", UUID.class),
                        rs.getString("business_name"), VendorStatus.valueOf(rs.getString("vendor_status")),
                        mediaUrl(rs.getString("storage_key"))), params.toArray());
        return new VendorDtos.VerificationQueueResponse(items, null);
    }

    @Transactional
    public VerificationDocument review(UUID reviewerId, UUID documentId, boolean approve,
            VendorDtos.VerificationReviewRequest request) {
        VerificationDocument current = document(documentId);
        if (!approve && (request == null || clean(request.note()) == null)) {
            throw ApiException.badRequest("VERIFICATION_NOTE_REQUIRED", "A rejection reason is required");
        }
        String note = request == null ? null : clean(request.note());
        jdbc.update("""
                UPDATE verification_documents SET status=?,reviewer_note=?,reviewed_by=?,reviewed_at=now(),
                    expires_at=COALESCE(?,expires_at),
                    updated_at=now() WHERE id=? AND deleted_at IS NULL
                """, approve ? "APPROVED" : "REJECTED", note, reviewerId,
                request == null ? null : request.expiresAt(), documentId);
        UUID vendorId = jdbc.queryForObject(
                "SELECT vendor_id FROM verification_documents WHERE id=?", UUID.class, documentId);
        if (approve) {
            String badge = switch (current.type()) {
                case GOVERNMENT_ID -> "IDENTITY_VERIFIED";
                case BUSINESS_REGISTRATION -> "BUSINESS_VERIFIED";
                case INSURANCE -> "INSURED";
                case PORTFOLIO_PROOF -> null;
            };
            if (badge != null) {
                Instant expiry = current.type() == DocumentType.INSURANCE && request != null
                        ? request.expiresAt() : null;
                jdbc.update("""
                        INSERT INTO vendor_badges(id,vendor_id,badge,expires_at,created_by)
                        VALUES (?,?,?,?,?) ON CONFLICT (vendor_id,badge) DO UPDATE
                        SET granted_at=now(),expires_at=EXCLUDED.expires_at,deleted_at=NULL,updated_at=now()
                        """, Uuidv7.next(), vendorId, badge, expiry, reviewerId);
            }
            maybeVerify(vendorId);
        } else if (current.type() == DocumentType.GOVERNMENT_ID
                || current.type() == DocumentType.BUSINESS_REGISTRATION) {
            jdbc.update("""
                    UPDATE vendor_profiles SET status='REJECTED',rejection_reason=?,updated_at=now()
                    WHERE account_id=? AND NOT is_public
                    """, note, vendorId);
        }
        auditService.record(reviewerId, approve ? "verification.approved" : "verification.rejected",
                "VerificationDocument", documentId.toString(), null);
        return document(documentId);
    }

    private VendorDtos.VendorMeResponse workspace(UUID accountId) {
        List<SubmitGate> gateList = gates(accountId);
        int strength = (int) Math.round(gateList.stream().filter(SubmitGate::passed).count()
                * 100.0 / gateList.size());
        return new VendorDtos.VendorMeResponse(profile(accountId, false), categorySelections(accountId),
                serviceAreas(accountId), packages(accountId), addOns(accountId), media(accountId), faqs(accountId),
                documents(accountId), badges(accountId), gateList, strength);
    }

    private void ensureProfile(UUID accountId) {
        jdbc.update("""
                INSERT INTO vendor_profiles(account_id,currency,created_by)
                SELECT a.id,a.default_currency,a.id FROM accounts a
                WHERE a.id=? AND EXISTS (SELECT 1 FROM account_roles r
                    WHERE r.account_id=a.id AND r.role='VENDOR' AND r.deleted_at IS NULL)
                ON CONFLICT (account_id) DO NOTHING
                """, accountId);
        if (!exists("SELECT count(*) FROM vendor_profiles WHERE account_id=?", accountId)) {
            throw ApiException.forbidden("VENDOR_ROLE_REQUIRED", "Add the vendor role to continue");
        }
    }

    private VendorProfile profile(UUID accountId, boolean publicRead) {
        try {
            return jdbc.queryForObject("""
                    SELECT account_id,business_name,slug,tagline,about,founded_year,team_size,languages,
                        base_city,base_country,lat,lng,website,instagram,status,rejection_reason,
                        onboarding_step,currency FROM vendor_profiles WHERE account_id=? AND deleted_at IS NULL
                    """, this::mapProfile, accountId);
        } catch (EmptyResultDataAccessException ex) {
            throw notFound(publicRead ? "Vendor" : "Vendor profile");
        }
    }

    private List<SubmitGate> gates(UUID vendorId) {
        VendorProfile p = profile(vendorId, false);
        return List.of(
                gate("business_basics", 1, present(p.businessName()) && present(p.baseCity())
                        && present(p.baseCountry()), "Add business name, city, and country"),
                gate("about_200", 1, p.about() != null && p.about().trim().length() >= 200,
                        "About must be at least 200 characters"),
                gate("category", 2, exists("SELECT count(*) FROM vendor_categories WHERE vendor_id=? AND deleted_at IS NULL", vendorId),
                        "Choose at least one category"),
                gate("service_area", 3, exists("SELECT count(*) FROM service_areas WHERE vendor_id=? AND deleted_at IS NULL", vendorId),
                        "Add at least one service area"),
                gate("published_package", 4, exists("SELECT count(*) FROM packages WHERE vendor_id=? AND status='PUBLISHED' AND deleted_at IS NULL", vendorId),
                        "Publish at least one priced package"),
                gate("gallery_min_5", 5, count("SELECT count(*) FROM vendor_media WHERE vendor_id=? AND kind='GALLERY' AND deleted_at IS NULL", vendorId) >= 5,
                        "Add at least 5 gallery photos"),
                gate("cover", 5, exists("SELECT count(*) FROM vendor_media WHERE vendor_id=? AND kind='COVER' AND deleted_at IS NULL", vendorId),
                        "Choose a cover photo"),
                gate("required_documents", 6, requiredDocsReady(vendorId),
                        "Upload government ID and business registration"));
    }

    private boolean requiredDocsReady(UUID vendorId) {
        return count("""
                SELECT count(DISTINCT type) FROM verification_documents
                WHERE vendor_id=? AND type IN ('GOVERNMENT_ID','BUSINESS_REGISTRATION')
                    AND status IN ('PENDING','APPROVED') AND deleted_at IS NULL
                """, vendorId) == 2;
    }

    private void maybeVerify(UUID vendorId) {
        if (count("""
                SELECT count(DISTINCT type) FROM verification_documents
                WHERE vendor_id=? AND type IN ('GOVERNMENT_ID','BUSINESS_REGISTRATION')
                    AND status='APPROVED' AND deleted_at IS NULL
                """, vendorId) != 2) return;
        VendorProfile p = profile(vendorId, false);
        if (p.status() == VendorStatus.SUBMITTED || p.status() == VendorStatus.UNDER_REVIEW
                || p.status() == VendorStatus.REJECTED) {
            jdbc.update("""
                    UPDATE vendor_profiles SET status='VERIFIED',is_public=true,rejection_reason=NULL,
                        updated_at=now() WHERE account_id=?
                    """, vendorId);
            accountRepository.findById(vendorId).ifPresent(account ->
                    mailService.sendVendorVerified(account.getEmail(), p.businessName(),
                            properties.publicWebUrl() + "/v/" + p.slug()));
        }
    }

    private List<VendorCategorySelection> categorySelections(UUID id) {
        return jdbc.query("""
                SELECT category_id,is_primary FROM vendor_categories
                WHERE vendor_id=? AND deleted_at IS NULL ORDER BY is_primary DESC,created_at
                """, (rs, n) -> new VendorCategorySelection(rs.getObject(1, UUID.class), rs.getBoolean(2)), id);
    }

    private List<Category> selectedCategories(UUID id) {
        return jdbc.query("""
                SELECT c.id,c.slug,c.name,c.parent_id,c.icon,c.sort,c.is_active
                FROM categories c JOIN vendor_categories vc ON vc.category_id=c.id
                WHERE vc.vendor_id=? AND vc.deleted_at IS NULL ORDER BY vc.is_primary DESC,c.sort
                """, this::mapCategory, id);
    }

    private List<ServiceArea> serviceAreas(UUID id) {
        return jdbc.query("""
                SELECT id,mode,city,country,lat,lng,radius_km,travel_fee_cents,travel_fee_note
                FROM service_areas WHERE vendor_id=? AND deleted_at IS NULL ORDER BY created_at
                """, this::mapServiceArea, id);
    }

    private ServiceArea serviceArea(UUID vendorId, UUID id) {
        try {
            return jdbc.queryForObject("""
                    SELECT id,mode,city,country,lat,lng,radius_km,travel_fee_cents,travel_fee_note
                    FROM service_areas WHERE id=? AND vendor_id=? AND deleted_at IS NULL
                    """, this::mapServiceArea, id, vendorId);
        } catch (EmptyResultDataAccessException ex) { throw notFound("Service area"); }
    }

    private List<PackageDto> packages(UUID id) {
        return jdbc.query(packageSelect() + " WHERE p.vendor_id=? AND p.deleted_at IS NULL AND p.status<>'ARCHIVED' ORDER BY p.sort,p.created_at",
                this::mapPackage, id);
    }

    private List<PackageDto> publicPackages(UUID id) {
        return jdbc.query(packageSelect() + " WHERE p.vendor_id=? AND p.deleted_at IS NULL AND p.status='PUBLISHED' ORDER BY p.sort,p.created_at",
                this::mapPackage, id);
    }

    private PackageDto packageById(UUID vendorId, UUID id) {
        try {
            return jdbc.queryForObject(packageSelect() + " WHERE p.id=? AND p.vendor_id=? AND p.deleted_at IS NULL",
                    this::mapPackage, id, vendorId);
        } catch (EmptyResultDataAccessException ex) { throw notFound("Package"); }
    }

    private String packageSelect() {
        return """
                SELECT p.id,p.vendor_id,p.category_id,p.title,p.slug,p.description_md,p.price_cents,
                    p.currency,p.pricing_model,p.min_guests,p.max_guests,p.duration_minutes,
                    p.whats_included_md,p.whats_excluded_md,p.booking_mode,p.deposit_pct,
                    p.cancellation_policy,p.status,p.cover_media_id,p.sort,m.storage_key cover_storage_key
                FROM packages p LEFT JOIN media_assets m ON m.id=p.cover_media_id
                """;
    }

    private List<AddOn> addOns(UUID id) {
        return jdbc.query("""
                SELECT id,package_id,title,price_cents,pricing_model,max_qty,description
                FROM add_ons WHERE vendor_id=? AND deleted_at IS NULL ORDER BY created_at
                """, this::mapAddOn, id);
    }

    private AddOn addOn(UUID vendorId, UUID id) {
        try {
            return jdbc.queryForObject("""
                    SELECT id,package_id,title,price_cents,pricing_model,max_qty,description
                    FROM add_ons WHERE id=? AND vendor_id=? AND deleted_at IS NULL
                    """, this::mapAddOn, id, vendorId);
        } catch (EmptyResultDataAccessException ex) { throw notFound("Add-on"); }
    }

    private List<VendorMediaItem> media(UUID id) {
        return jdbc.query("""
                SELECT vm.id,vm.media_id,vm.kind,vm.caption,vm.sort,m.storage_key,m.width,m.height,m.blurhash
                FROM vendor_media vm JOIN media_assets m ON m.id=vm.media_id
                WHERE vm.vendor_id=? AND vm.deleted_at IS NULL ORDER BY vm.kind,vm.sort
                """, this::mapMedia, id);
    }

    private List<VendorFaqItem> faqs(UUID id) {
        return jdbc.query("""
                SELECT question,answer_md FROM vendor_faqs
                WHERE vendor_id=? AND deleted_at IS NULL ORDER BY sort
                """, (rs, n) -> new VendorFaqItem(rs.getString(1), rs.getString(2)), id);
    }

    private List<VerificationDocument> documents(UUID id) {
        return jdbc.query("""
                SELECT id,type,status,reviewer_note,reviewed_at,created_at FROM verification_documents
                WHERE vendor_id=? AND deleted_at IS NULL ORDER BY created_at DESC
                """, this::mapDocument, id);
    }

    private VerificationDocument document(UUID id) {
        try {
            return jdbc.queryForObject("""
                    SELECT id,type,status,reviewer_note,reviewed_at,created_at
                    FROM verification_documents WHERE id=? AND deleted_at IS NULL
                    """, this::mapDocument, id);
        } catch (EmptyResultDataAccessException ex) { throw notFound("Verification document"); }
    }

    private List<VendorBadge> badges(UUID id) {
        return jdbc.query("""
                SELECT badge,granted_at,expires_at FROM vendor_badges
                WHERE vendor_id=? AND deleted_at IS NULL AND (expires_at IS NULL OR expires_at>now())
                ORDER BY granted_at
                """, (rs, n) -> new VendorBadge(rs.getString(1), instant(rs, 2), instant(rs, 3)), id);
    }

    private void validateArea(ServiceAreaRequest request) {
        if (!AREA_MODES.contains(request.mode())) {
            throw ApiException.badRequest("SERVICE_AREA_MODE_INVALID", "Mode must be CITY_RADIUS or REGION");
        }
        if (request.mode().equals("CITY_RADIUS")
                && (request.lat() == null || request.lng() == null || request.radiusKm() == null)) {
            throw ApiException.badRequest("SERVICE_AREA_LOCATION_REQUIRED",
                    "City-radius areas require latitude, longitude, and radius");
        }
    }

    private void validatePackage(UUID accountId, PackageRequest request, boolean publishing) {
        requireCategory(request.categoryId());
        if (request.maxGuests() != null && request.minGuests() != null
                && request.maxGuests() < request.minGuests()) {
            throw ApiException.badRequest("PACKAGE_GUEST_RANGE_INVALID", "Maximum guests must be at least minimum guests");
        }
        if (request.coverMediaId() != null) {
            requireMedia(accountId, request.coverMediaId(), Set.of(MediaAsset.Kind.IMAGE));
        }
        if (publishing && (includedCount(request.whatsIncludedMd()) < 3 || request.coverMediaId() == null)) {
            throw ApiException.badRequest("PACKAGE_PUBLISHED_GATES",
                    "Published packages must keep 3 included items and a package photo");
        }
    }

    private void validateAddOn(UUID accountId, AddOnRequest request) {
        if (request.priceCents() <= 0) {
            throw ApiException.badRequest("ADD_ON_PRICE_REQUIRED", "Add-on price must be greater than zero");
        }
        if (request.packageId() != null) packageById(accountId, request.packageId());
    }

    private MediaAsset requireMedia(UUID owner, UUID id, Set<MediaAsset.Kind> kinds) {
        MediaAsset asset = mediaRepository.findById(id)
                .orElseThrow(() -> notFound("Media asset"));
        if (!asset.getOwnerAccountId().equals(owner)) {
            throw ApiException.forbidden("MEDIA_FORBIDDEN", "Not your media asset");
        }
        if (asset.getStatus() != MediaAsset.Status.READY || !kinds.contains(asset.getKind())) {
            throw ApiException.badRequest("MEDIA_NOT_READY", "Use a completed upload of the correct file type");
        }
        return asset;
    }

    private void requireCategory(UUID id) {
        if (!exists("SELECT count(*) FROM categories WHERE id=? AND is_active AND deleted_at IS NULL", id)) {
            throw ApiException.badRequest("CATEGORY_INVALID", "Choose an active category");
        }
    }

    private boolean hasPublishedPackage(UUID accountId) {
        return exists("SELECT count(*) FROM packages WHERE vendor_id=? AND status='PUBLISHED' AND deleted_at IS NULL", accountId);
    }

    private void advance(UUID id, int step) {
        jdbc.update("UPDATE vendor_profiles SET onboarding_step=GREATEST(onboarding_step,?),updated_at=now() WHERE account_id=?",
                step, id);
    }

    private String uniqueSlug(String input) {
        String base = slugify(input);
        String candidate = base;
        int suffix = 2;
        while (exists("SELECT count(*) FROM vendor_profiles WHERE slug=?", candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String uniquePackageSlug(UUID vendorId, String input, UUID excluded) {
        String base = slugify(input);
        String candidate = base;
        int suffix = 2;
        while ((excluded == null
                ? count("SELECT count(*) FROM packages WHERE vendor_id=? AND slug=?", vendorId, candidate)
                : count("SELECT count(*) FROM packages WHERE vendor_id=? AND slug=? AND id<>?",
                        vendorId, candidate, excluded)) > 0) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static String slugify(String input) {
        String value = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return value.isBlank() ? "vendor" : value.substring(0, Math.min(150, value.length()));
    }

    private boolean exists(String sql, Object... params) { return count(sql, params) > 0; }
    private long count(String sql, Object... params) {
        Long result = jdbc.queryForObject(sql, Long.class, params);
        return result == null ? 0 : result;
    }

    private Array sqlArray(List<String> values) {
        if (values == null) return null;
        return jdbc.execute((ConnectionCallback<Array>) connection -> connection.createArrayOf("text",
                values.stream().map(String::trim).filter(s -> !s.isBlank()).toArray(String[]::new)));
    }

    private String mediaUrl(String storageKey) {
        return storageKey == null ? null : properties.media().publicBaseUrl() + "/" + storageKey;
    }

    private Category mapCategory(ResultSet rs, int n) throws SQLException {
        return new Category(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
                rs.getObject("parent_id", UUID.class), rs.getString("icon"), rs.getInt("sort"), rs.getBoolean("is_active"));
    }

    private VendorProfile mapProfile(ResultSet rs, int n) throws SQLException {
        Array array = rs.getArray("languages");
        List<String> languages = array == null ? List.of() : List.of((String[]) array.getArray());
        return new VendorProfile(rs.getObject("account_id", UUID.class), rs.getString("business_name"),
                rs.getString("slug"), rs.getString("tagline"), rs.getString("about"),
                integer(rs, "founded_year"), integer(rs, "team_size"), languages, rs.getString("base_city"),
                rs.getString("base_country"), decimal(rs, "lat"), decimal(rs, "lng"), rs.getString("website"),
                rs.getString("instagram"), VendorStatus.valueOf(rs.getString("status")),
                rs.getString("rejection_reason"), rs.getInt("onboarding_step"), rs.getString("currency"));
    }

    private ServiceArea mapServiceArea(ResultSet rs, int n) throws SQLException {
        return new ServiceArea(rs.getObject("id", UUID.class), rs.getString("mode"), rs.getString("city"),
                rs.getString("country"), decimal(rs, "lat"), decimal(rs, "lng"), integer(rs, "radius_km"),
                longValue(rs, "travel_fee_cents"), rs.getString("travel_fee_note"));
    }

    private PackageDto mapPackage(ResultSet rs, int n) throws SQLException {
        return new PackageDto(rs.getObject("id", UUID.class), rs.getObject("vendor_id", UUID.class),
                rs.getObject("category_id", UUID.class), rs.getString("title"), rs.getString("slug"),
                rs.getString("description_md"), rs.getLong("price_cents"), rs.getString("currency"),
                VendorDtos.PricingModel.valueOf(rs.getString("pricing_model")), integer(rs, "min_guests"),
                integer(rs, "max_guests"), integer(rs, "duration_minutes"), rs.getString("whats_included_md"),
                rs.getString("whats_excluded_md"), VendorDtos.BookingMode.valueOf(rs.getString("booking_mode")),
                rs.getInt("deposit_pct"), VendorDtos.CancellationPolicy.valueOf(rs.getString("cancellation_policy")),
                PackageStatus.valueOf(rs.getString("status")), rs.getObject("cover_media_id", UUID.class),
                mediaUrl(rs.getString("cover_storage_key")), rs.getInt("sort"));
    }

    private AddOn mapAddOn(ResultSet rs, int n) throws SQLException {
        return new AddOn(rs.getObject("id", UUID.class), rs.getObject("package_id", UUID.class),
                rs.getString("title"), rs.getLong("price_cents"),
                VendorDtos.PricingModel.valueOf(rs.getString("pricing_model")), integer(rs, "max_qty"),
                rs.getString("description"));
    }

    private VendorMediaItem mapMedia(ResultSet rs, int n) throws SQLException {
        return new VendorMediaItem(rs.getObject("id", UUID.class), rs.getObject("media_id", UUID.class),
                MediaKind.valueOf(rs.getString("kind")), rs.getString("caption"), rs.getInt("sort"),
                mediaUrl(rs.getString("storage_key")), integer(rs, "width"), integer(rs, "height"),
                rs.getString("blurhash"));
    }

    private VerificationDocument mapDocument(ResultSet rs, int n) throws SQLException {
        return new VerificationDocument(rs.getObject("id", UUID.class), DocumentType.valueOf(rs.getString("type")),
                DocumentStatus.valueOf(rs.getString("status")), rs.getString("reviewer_note"),
                instant(rs, "reviewed_at"), instant(rs, "created_at"));
    }

    private static Integer integer(ResultSet rs, String name) throws SQLException {
        int value = rs.getInt(name); return rs.wasNull() ? null : value;
    }
    private static Long longValue(ResultSet rs, String name) throws SQLException {
        long value = rs.getLong(name); return rs.wasNull() ? null : value;
    }
    private static Double decimal(ResultSet rs, String name) throws SQLException {
        double value = rs.getDouble(name); return rs.wasNull() ? null : value;
    }
    private static Instant instant(ResultSet rs, String name) throws SQLException {
        var value = rs.getTimestamp(name); return value == null ? null : value.toInstant();
    }
    private static Instant instant(ResultSet rs, int index) throws SQLException {
        var value = rs.getTimestamp(index); return value == null ? null : value.toInstant();
    }
    private static String clean(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private static String upper(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static String normalizeCurrency(String value) {
        if (value == null) return null;
        String currency = upper(value);
        if (!CURRENCIES.contains(currency)) throw ApiException.badRequest("CURRENCY_INVALID", "Unsupported currency");
        return currency;
    }
    private static int includedCount(String markdown) {
        if (markdown == null) return 0;
        return (int) markdown.lines().map(String::trim).map(s -> s.replaceFirst("^[-*]\\s*", ""))
                .filter(s -> !s.isBlank()).count();
    }
    private static <T> T value(T value, T fallback) { return value == null ? fallback : value; }
    private static SubmitGate gate(String key, int step, boolean passed, String message) {
        return new SubmitGate(key, step, passed, message);
    }
    private static ErrorResponse.FieldError field(String name, String message) {
        return new ErrorResponse.FieldError(name, message);
    }
    private static ApiException notFound(String thing) {
        return ApiException.notFound("VENDOR_RESOURCE_NOT_FOUND", thing + " not found");
    }
}
