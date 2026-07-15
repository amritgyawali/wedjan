package com.wedjan.api.media;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class MediaDtos {

    private MediaDtos() {}

    public record PresignRequest(
            @NotNull MediaAsset.Kind kind,
            @NotBlank @Size(max = 255) String mime,
            @Min(1) long bytes) {}

    public record PresignResponse(
            UUID mediaId, String uploadUrl, int expiresInSeconds, Map<String, String> headers) {}

    public record MediaAssetDto(
            UUID id,
            String kind,
            String mime,
            long bytes,
            Integer width,
            Integer height,
            String blurhash,
            String status,
            String url,
            Instant createdAt) {}
}
