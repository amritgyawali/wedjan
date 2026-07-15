package com.wedjan.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view over the wedjan.* configuration tree. */
@ConfigurationProperties(prefix = "wedjan")
public record WedjanProperties(Auth auth, Cors cors, Mail mail, Media media, Seed seed) {

    public record Auth(
            String jwtSecret,
            long accessTokenTtlSeconds,
            long refreshTokenTtlDays,
            long otpTtlMinutes,
            int otpMaxAttempts,
            String refreshCookieName,
            boolean refreshCookieSecure) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Mail(String from) {}

    public record Media(
            String endpoint,
            String region,
            String accessKey,
            String secretKey,
            String bucket,
            String publicBaseUrl,
            long presignTtlMinutes,
            long maxImageBytes,
            long maxVideoBytes,
            long maxDocBytes) {}

    public record Seed(boolean enabled, String demoPassword) {}
}
