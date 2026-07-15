package com.wedjan.api.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3-compatible storage client — Cloudflare R2 in production, MinIO locally.
 * Path-style access is required by MinIO and harmless for R2.
 */
@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(WedjanProperties properties) {
        WedjanProperties.Media media = properties.media();
        return S3Client.builder()
                .endpointOverride(URI.create(media.endpoint()))
                .region(Region.of(media.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(media.accessKey(), media.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(WedjanProperties properties) {
        WedjanProperties.Media media = properties.media();
        return S3Presigner.builder()
                .endpointOverride(URI.create(media.endpoint()))
                .region(Region.of(media.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(media.accessKey(), media.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
