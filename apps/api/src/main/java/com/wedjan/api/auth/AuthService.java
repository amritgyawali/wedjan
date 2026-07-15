package com.wedjan.api.auth;

import com.wedjan.api.account.Account;
import com.wedjan.api.account.AccountDtos;
import com.wedjan.api.account.AccountRepository;
import com.wedjan.api.account.AccountRole;
import com.wedjan.api.account.AccountRoleRepository;
import com.wedjan.api.account.AccountService;
import com.wedjan.api.account.Profile;
import com.wedjan.api.account.ProfileRepository;
import com.wedjan.api.audit.AuditService;
import com.wedjan.api.common.ApiException;
import com.wedjan.api.common.Sha256;
import com.wedjan.api.common.Uuidv7;
import com.wedjan.api.config.WedjanProperties;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** Constant-time-ish dummy hash compared against for unknown emails (timing). */
    private static final String DUMMY_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$YWJjZGVmZ2hpamtsbW5vcA$L5N1cIVvzXg1cQrxKcMxUmYl+w0k5PXQpPcT5f8U0GM";

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final ProfileRepository profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailService mailService;
    private final AccountService accountService;
    private final AuditService auditService;
    private final WedjanProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            AccountRepository accountRepository,
            AccountRoleRepository accountRoleRepository,
            ProfileRepository profileRepository,
            RefreshTokenRepository refreshTokenRepository,
            EmailVerificationRepository emailVerificationRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            MailService mailService,
            AccountService accountService,
            AuditService auditService,
            WedjanProperties properties) {
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.profileRepository = profileRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mailService = mailService;
        this.accountService = accountService;
        this.auditService = auditService;
        this.properties = properties;
    }

    // -------------------------------------------------------------------
    // Signup & verification
    // -------------------------------------------------------------------

    @Transactional
    public void signup(AuthDtos.SignupRequest request) {
        var existing = accountRepository.findActiveByEmail(request.email());
        if (existing.isPresent()) {
            Account account = existing.get();
            if (account.getStatus() == Account.Status.PENDING_VERIFICATION) {
                issueOtp(account, EmailVerification.Purpose.SIGNUP);
            } else {
                mailService.sendAccountExistsNotice(account.getEmail());
            }
            return; // response is identical either way — no enumeration
        }

        Account account = Account.create(request.email(), passwordEncoder.encode(request.password()));
        if (request.locale() != null) account.setLocale(request.locale());
        if (request.defaultCurrency() != null) account.setDefaultCurrency(request.defaultCurrency());
        if (request.marketingOptIn() != null) account.setMarketingOptIn(request.marketingOptIn());
        accountRepository.save(account);
        accountRoleRepository.save(AccountRole.grant(account.getId(), AccountRole.Role.valueOf(request.role())));
        profileRepository.save(Profile.create(account.getId()));
        issueOtp(account, EmailVerification.Purpose.SIGNUP);
        auditService.record(account.getId(), "account.signup", "Account", account.getId().toString(),
                Map.of("role", request.role()));
    }

    // noRollbackFor: the OTP attempt counter must survive the "invalid code"
    // exception, or the 5-attempt lockout could never engage.
    @Transactional(noRollbackFor = ApiException.class)
    public void verifyEmail(AuthDtos.VerifyEmailRequest request) {
        Account account = accountRepository.findActiveByEmail(request.email())
                .orElseThrow(AuthService::invalidCode);
        consumeOtp(account, EmailVerification.Purpose.SIGNUP, request.code());
        if (account.getStatus() == Account.Status.PENDING_VERIFICATION) {
            account.setStatus(Account.Status.ACTIVE);
            accountRepository.save(account);
        }
        auditService.record(account.getId(), "account.email_verified", "Account",
                account.getId().toString(), Map.of());
    }

    // -------------------------------------------------------------------
    // Login / refresh / logout
    // -------------------------------------------------------------------

    @Transactional
    public AuthDtos.TokenBundle login(AuthDtos.LoginRequest request, String ip) {
        var maybeAccount = accountRepository.findActiveByEmail(request.email());
        if (maybeAccount.isEmpty()) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw invalidCredentials();
        }
        Account account = maybeAccount.get();
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw invalidCredentials();
        }
        switch (account.getStatus()) {
            case PENDING_VERIFICATION -> throw ApiException.unauthorized(
                    "AUTH_EMAIL_NOT_VERIFIED", "Verify your email before logging in");
            case SUSPENDED, DELETED -> throw ApiException.unauthorized(
                    "AUTH_ACCOUNT_DISABLED", "This account is not available");
            case ACTIVE -> { /* proceed */ }
        }

        String rawRefresh = newOpaqueToken();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(properties.auth().refreshTokenTtlDays()));
        refreshTokenRepository.save(RefreshToken.create(
                account.getId(), Uuidv7.next(), Sha256.hex(rawRefresh),
                request.deviceName(), ip, expiresAt, null));

        auditService.record(account.getId(), "auth.login", "Account", account.getId().toString(), Map.of());
        return bundle(account, rawRefresh);
    }

    // noRollbackFor: reuse detection revokes the token family and THEN throws
    // 401 — the revocation must not be rolled back with the exception.
    @Transactional(noRollbackFor = ApiException.class)
    public AuthDtos.TokenBundle refresh(String rawRefreshToken, String ip) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw refreshInvalid();
        }
        RefreshToken presented = refreshTokenRepository.findByTokenHash(Sha256.hex(rawRefreshToken))
                .orElseThrow(AuthService::refreshInvalid);

        Instant now = Instant.now();
        if (presented.getRevokedAt() != null) {
            // Reuse of a rotated token — revoke the whole family, force re-login.
            refreshTokenRepository.revokeFamily(presented.getFamilyId(), now);
            auditService.record(presented.getAccountId(), "auth.refresh_reuse_detected", "RefreshToken",
                    presented.getId().toString(), Map.of("familyId", presented.getFamilyId().toString()));
            throw ApiException.unauthorized("AUTH_REFRESH_REUSED",
                    "Session invalidated for your protection — please log in again");
        }
        if (presented.getExpiresAt().isBefore(now)) {
            presented.setRevokedAt(now);
            refreshTokenRepository.save(presented);
            throw refreshInvalid();
        }

        Account account = accountRepository.findById(presented.getAccountId())
                .orElseThrow(AuthService::refreshInvalid);
        if (account.getStatus() != Account.Status.ACTIVE) {
            refreshTokenRepository.revokeFamily(presented.getFamilyId(), now);
            throw ApiException.unauthorized("AUTH_ACCOUNT_DISABLED", "This account is not available");
        }

        presented.setRevokedAt(now);
        refreshTokenRepository.save(presented);
        String rawRefresh = newOpaqueToken();
        refreshTokenRepository.save(RefreshToken.create(
                account.getId(), presented.getFamilyId(), Sha256.hex(rawRefresh),
                presented.getDeviceName(), ip,
                now.plus(Duration.ofDays(properties.auth().refreshTokenTtlDays())),
                presented.getId()));

        return bundle(account, rawRefresh);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(Sha256.hex(rawRefreshToken)).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    @Transactional
    public void logoutAll(UUID accountId) {
        refreshTokenRepository.revokeAllForAccount(accountId, Instant.now());
        auditService.record(accountId, "auth.logout_all", "Account", accountId.toString(), Map.of());
    }

    // -------------------------------------------------------------------
    // Password reset
    // -------------------------------------------------------------------

    @Transactional
    public void requestPasswordReset(String email) {
        accountRepository.findActiveByEmail(email).ifPresent(account -> {
            if (account.getStatus() == Account.Status.ACTIVE) {
                String code = newOtpCode();
                emailVerificationRepository.save(EmailVerification.create(
                        account.getId(), Sha256.hex(code), EmailVerification.Purpose.PASSWORD_RESET,
                        Instant.now().plus(Duration.ofMinutes(properties.auth().otpTtlMinutes()))));
                mailService.sendPasswordResetOtp(account.getEmail(), code);
            }
        });
        // Always the same generic 202 — no enumeration.
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void confirmPasswordReset(AuthDtos.PasswordResetConfirmRequest request) {
        Account account = accountRepository.findActiveByEmail(request.email())
                .orElseThrow(AuthService::invalidCode);
        consumeOtp(account, EmailVerification.Purpose.PASSWORD_RESET, request.code());
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        accountRepository.save(account);
        refreshTokenRepository.revokeAllForAccount(account.getId(), Instant.now());
        auditService.record(account.getId(), "auth.password_reset", "Account",
                account.getId().toString(), Map.of());
    }

    // -------------------------------------------------------------------
    // Roles & sessions
    // -------------------------------------------------------------------

    @Transactional
    public AuthDtos.RolesResponse addRole(UUID accountId, String role) {
        AccountRole.Role parsed = AccountRole.Role.valueOf(role);
        var existing = accountRoleRepository.findByAccountIdAndRoleAndDeletedAtIsNull(accountId, parsed);
        if (existing.isEmpty()) {
            accountRoleRepository.save(AccountRole.grant(accountId, parsed));
            auditService.record(accountId, "account.role_added", "Account", accountId.toString(),
                    Map.of("role", role));
        }
        List<String> roles = accountRoleRepository.findByAccountIdAndDeletedAtIsNull(accountId).stream()
                .map(r -> r.getRole().name())
                .toList();
        return new AuthDtos.RolesResponse(roles);
    }

    @Transactional(readOnly = true)
    public AccountDtos.SessionListResponse listSessions(UUID accountId, String currentRawToken) {
        String currentHash = currentRawToken == null ? null : Sha256.hex(currentRawToken);
        List<AccountDtos.SessionDto> sessions = refreshTokenRepository
                .findByAccountIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(accountId, Instant.now())
                .stream()
                .map(token -> new AccountDtos.SessionDto(
                        token.getId(), token.getDeviceName(), token.getIp(),
                        token.getCreatedAt(), token.getExpiresAt(),
                        token.getTokenHash().equals(currentHash)))
                .toList();
        return new AccountDtos.SessionListResponse(sessions);
    }

    @Transactional
    public void revokeSession(UUID accountId, UUID sessionId) {
        RefreshToken token = refreshTokenRepository.findById(sessionId)
                .filter(t -> t.getAccountId().equals(accountId) && t.getRevokedAt() == null)
                .orElseThrow(() -> ApiException.notFound("SESSION_NOT_FOUND", "Session not found"));
        token.setRevokedAt(Instant.now());
        refreshTokenRepository.save(token);
    }

    // -------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------

    private AuthDtos.TokenBundle bundle(Account account, String rawRefresh) {
        AccountDtos.MeResponse me = accountService.me(account.getId());
        String accessToken = jwtService.generateAccessToken(account.getId(), account.getEmail(), me.roles());
        return new AuthDtos.TokenBundle(accessToken, jwtService.accessTokenTtlSeconds(), rawRefresh, me);
    }

    private void issueOtp(Account account, EmailVerification.Purpose purpose) {
        String code = newOtpCode();
        emailVerificationRepository.save(EmailVerification.create(
                account.getId(), Sha256.hex(code), purpose,
                Instant.now().plus(Duration.ofMinutes(properties.auth().otpTtlMinutes()))));
        if (purpose == EmailVerification.Purpose.SIGNUP) {
            mailService.sendSignupOtp(account.getEmail(), code);
        } else {
            mailService.sendPasswordResetOtp(account.getEmail(), code);
        }
    }

    private void consumeOtp(Account account, EmailVerification.Purpose purpose, String code) {
        EmailVerification verification = emailVerificationRepository
                .findTopByAccountIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(account.getId(), purpose)
                .orElseThrow(AuthService::invalidCode);
        if (verification.getExpiresAt().isBefore(Instant.now())) {
            throw invalidCode();
        }
        if (verification.getAttempts() >= properties.auth().otpMaxAttempts()) {
            throw ApiException.badRequest("AUTH_CODE_LOCKED",
                    "Too many incorrect attempts — request a new code");
        }
        verification.setAttempts((short) (verification.getAttempts() + 1));
        emailVerificationRepository.save(verification);
        if (!verification.getCodeHash().equals(Sha256.hex(code))) {
            throw invalidCode();
        }
        verification.setConsumedAt(Instant.now());
        emailVerificationRepository.save(verification);
    }

    private String newOtpCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String newOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static ApiException invalidCode() {
        return ApiException.badRequest("AUTH_INVALID_CODE", "Invalid or expired code");
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("AUTH_INVALID_CREDENTIALS", "Invalid email or password");
    }

    private static ApiException refreshInvalid() {
        return ApiException.unauthorized("AUTH_REFRESH_INVALID", "Session expired — please log in again");
    }
}
