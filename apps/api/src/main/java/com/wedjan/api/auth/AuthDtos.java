package com.wedjan.api.auth;

import com.wedjan.api.account.AccountDtos;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {}

    public record SignupRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 10, max = 128) String password,
            @NotBlank @Pattern(regexp = "CUSTOMER|VENDOR|FREELANCER", message = "must be CUSTOMER, VENDOR or FREELANCER")
            String role,
            @Size(max = 10) String locale,
            @Pattern(regexp = "AUD|USD|GBP|NPR", message = "unsupported currency") String defaultCurrency,
            Boolean marketingOptIn) {}

    public record VerifyEmailRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "must be a 6-digit code") String code) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            @Size(max = 120) String deviceName) {}

    public record RefreshRequest(String refreshToken) {}

    public record PasswordResetRequest(@NotBlank @Email String email) {}

    public record PasswordResetConfirmRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "must be a 6-digit code") String code,
            @NotBlank @Size(min = 10, max = 128) String newPassword) {}

    public record AddRoleRequest(
            @NotBlank @Pattern(regexp = "CUSTOMER|VENDOR|FREELANCER", message = "must be CUSTOMER, VENDOR or FREELANCER")
            String role) {}

    public record RolesResponse(List<String> roles) {}

    public record MessageResponse(String message) {}

    public record AuthTokensResponse(
            String accessToken, long expiresInSeconds, String refreshToken, AccountDtos.MeResponse account) {}

    /** Internal bundle — the controller decides cookie vs body for the refresh token. */
    public record TokenBundle(
            String accessToken, long expiresInSeconds, String rawRefreshToken, AccountDtos.MeResponse me) {}
}
