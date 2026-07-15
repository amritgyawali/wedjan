package com.wedjan.api.media;

import com.wedjan.api.audit.AuditService;
import com.wedjan.api.common.ApiException;
import com.wedjan.api.config.WedjanProperties;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private static final Map<MediaAsset.Kind, Set<String>> ALLOWED_MIME = Map.of(
            MediaAsset.Kind.IMAGE, Set.of("image/jpeg", "image/png", "image/webp", "image/avif", "image/gif"),
            MediaAsset.Kind.VIDEO, Set.of("video/mp4", "video/quicktime", "video/webm"),
            MediaAsset.Kind.DOC, Set.of("application/pdf"));

    private static final Map<String, String> MIME_EXTENSION = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp",
            "image/avif", ".avif", "image/gif", ".gif",
            "video/mp4", ".mp4", "video/quicktime", ".mov", "video/webm", ".webm",
            "application/pdf", ".pdf");

    /** Never load more than this many bytes into memory for probing. */
    private static final long MAX_PROBE_BYTES = 15L * 1024 * 1024;

    private final MediaAssetRepository repository;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final WedjanProperties properties;
    private final AuditService auditService;

    public MediaService(MediaAssetRepository repository, S3Client s3Client, S3Presigner s3Presigner,
            WedjanProperties properties, AuditService auditService) {
        this.repository = repository;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Transactional
    public MediaDtos.PresignResponse presign(UUID ownerAccountId, MediaDtos.PresignRequest request) {
        Set<String> allowed = ALLOWED_MIME.get(request.kind());
        if (allowed == null || !allowed.contains(request.mime())) {
            throw ApiException.badRequest("MEDIA_UNSUPPORTED_TYPE",
                    "Unsupported file type for " + request.kind());
        }
        long maxBytes = switch (request.kind()) {
            case IMAGE -> properties.media().maxImageBytes();
            case VIDEO -> properties.media().maxVideoBytes();
            case DOC -> properties.media().maxDocBytes();
        };
        if (request.bytes() > maxBytes) {
            throw ApiException.badRequest("MEDIA_TOO_LARGE",
                    "File exceeds the " + (maxBytes / (1024 * 1024)) + "MB limit");
        }

        String extension = MIME_EXTENSION.getOrDefault(request.mime(), "");
        MediaAsset asset = MediaAsset.create(ownerAccountId, request.kind(), "pending", request.mime(),
                request.bytes());
        asset.setStorageKey("uploads/" + ownerAccountId + "/" + asset.getId() + extension);
        repository.save(asset);
        String storageKey = asset.getStorageKey();

        Duration ttl = Duration.ofMinutes(properties.media().presignTtlMinutes());
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(properties.media().bucket())
                .key(storageKey)
                .contentType(request.mime())
                .contentLength(request.bytes())
                .build();
        String uploadUrl = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .putObjectRequest(putRequest)
                        .build())
                .url()
                .toString();

        return new MediaDtos.PresignResponse(
                asset.getId(), uploadUrl, (int) ttl.toSeconds(),
                Map.of("Content-Type", request.mime()));
    }

    @Transactional
    public MediaDtos.MediaAssetDto complete(UUID ownerAccountId, UUID mediaId) {
        MediaAsset asset = repository.findById(mediaId)
                .orElseThrow(() -> ApiException.notFound("MEDIA_NOT_FOUND", "Media asset not found"));
        if (!asset.getOwnerAccountId().equals(ownerAccountId)) {
            throw ApiException.forbidden("MEDIA_FORBIDDEN", "Not your media asset");
        }
        if (asset.getStatus() == MediaAsset.Status.READY) {
            return toDto(asset); // idempotent
        }
        if (asset.getStatus() == MediaAsset.Status.BLOCKED) {
            throw ApiException.badRequest("MEDIA_BLOCKED", "This media asset was blocked");
        }

        HeadObjectResponse head;
        try {
            head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.media().bucket())
                    .key(asset.getStorageKey())
                    .build());
        } catch (NoSuchKeyException e) {
            throw ApiException.badRequest("MEDIA_NOT_UPLOADED", "Upload the file before completing");
        }
        asset.setBytes(head.contentLength());

        if (asset.getKind() == MediaAsset.Kind.IMAGE && head.contentLength() <= MAX_PROBE_BYTES) {
            probeImage(asset);
        }

        asset.setStatus(MediaAsset.Status.READY);
        repository.save(asset);
        auditService.record(ownerAccountId, "media.ready", "MediaAsset", asset.getId().toString(),
                Map.of("kind", asset.getKind().name(), "bytes", asset.getBytes()));
        return toDto(asset);
    }

    private void probeImage(MediaAsset asset) {
        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.media().bucket())
                    .key(asset.getStorageKey())
                    .build());
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(object.asByteArray()));
            if (image == null) {
                return; // format not decodable server-side (e.g. AVIF) — dims stay null
            }
            asset.setWidth(image.getWidth());
            asset.setHeight(image.getHeight());
            asset.setBlurhash(BlurHash.encode(thumbnail(image, 32), 4, 3));
        } catch (Exception e) {
            log.warn("Image probe failed for media {} — continuing without dimensions",
                    asset.getId(), e);
        }
    }

    private static BufferedImage thumbnail(BufferedImage source, int maxSize) {
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1.0, (double) maxSize / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return target;
    }

    private MediaDtos.MediaAssetDto toDto(MediaAsset asset) {
        String url = asset.getStatus() == MediaAsset.Status.READY
                ? properties.media().publicBaseUrl() + "/" + asset.getStorageKey()
                : null;
        return new MediaDtos.MediaAssetDto(
                asset.getId(), asset.getKind().name(), asset.getMime(), asset.getBytes(),
                asset.getWidth(), asset.getHeight(), asset.getBlurhash(),
                asset.getStatus().name(), url, asset.getCreatedAt());
    }
}
