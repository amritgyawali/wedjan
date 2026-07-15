package com.wedjan.api.account;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AccountDtos {

    private AccountDtos() {}

    public record AccountDto(
            UUID id,
            String email,
            String phone,
            String status,
            String locale,
            String defaultCurrency,
            boolean marketingOptIn,
            Instant createdAt) {

        public static AccountDto from(Account account) {
            return new AccountDto(
                    account.getId(),
                    account.getEmail(),
                    account.getPhone(),
                    account.getStatus().name(),
                    account.getLocale(),
                    account.getDefaultCurrency(),
                    account.isMarketingOptIn(),
                    account.getCreatedAt());
        }
    }

    public record ProfileDto(
            UUID accountId,
            String displayName,
            UUID avatarMediaId,
            String city,
            String country,
            String timezone) {

        public static ProfileDto from(Profile profile) {
            return new ProfileDto(
                    profile.getAccountId(),
                    profile.getDisplayName(),
                    profile.getAvatarMediaId(),
                    profile.getCity(),
                    profile.getCountry(),
                    profile.getTimezone());
        }
    }

    public record MeResponse(AccountDto account, List<String> roles, ProfileDto profile) {}

    public record UpdateProfileRequest(
            @Size(max = 120) String displayName,
            UUID avatarMediaId,
            @Size(max = 120) String city,
            @Size(min = 2, max = 2) String country,
            @Size(max = 64) String timezone,
            @Size(max = 10) String locale,
            @Pattern(regexp = "AUD|USD|GBP|NPR", message = "unsupported currency") String defaultCurrency,
            Boolean marketingOptIn) {}

    public record SessionDto(
            UUID id, String deviceName, String ip, Instant createdAt, Instant expiresAt, boolean current) {}

    public record SessionListResponse(List<SessionDto> items) {}
}
