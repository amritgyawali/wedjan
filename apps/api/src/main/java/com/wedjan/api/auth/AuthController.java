package com.wedjan.api.auth;

import com.wedjan.api.config.JwtAuthFilter;
import com.wedjan.api.config.WedjanProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String MOBILE = "mobile";

    private final AuthService authService;
    private final RateLimitService rateLimitService;
    private final WedjanProperties properties;

    public AuthController(AuthService authService, RateLimitService rateLimitService,
            WedjanProperties properties) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
        this.properties = properties;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthDtos.MessageResponse> signup(
            @Valid @RequestBody AuthDtos.SignupRequest request, HttpServletRequest http) {
        rateLimitService.check("signup", clientIp(http), 5, Duration.ofMinutes(1));
        authService.signup(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new AuthDtos.MessageResponse("Check your email for a verification code"));
    }

    @PostMapping("/verify-email")
    public AuthDtos.MessageResponse verifyEmail(
            @Valid @RequestBody AuthDtos.VerifyEmailRequest request, HttpServletRequest http) {
        rateLimitService.check("verify", clientIp(http), 10, Duration.ofMinutes(1));
        authService.verifyEmail(request);
        return new AuthDtos.MessageResponse("Email verified — you can log in now");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthTokensResponse> login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            @RequestHeader(name = "X-Wedjan-Client", required = false) String clientType,
            HttpServletRequest http) {
        rateLimitService.check("login", clientIp(http), 5, Duration.ofMinutes(1));
        AuthDtos.TokenBundle bundle = authService.login(request, clientIp(http));
        return tokensResponse(bundle, clientType);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthDtos.AuthTokensResponse> refresh(
            @RequestBody(required = false) AuthDtos.RefreshRequest request,
            @RequestHeader(name = "X-Wedjan-Client", required = false) String clientType,
            HttpServletRequest http) {
        String raw = request != null && request.refreshToken() != null && !request.refreshToken().isBlank()
                ? request.refreshToken()
                : readRefreshCookie(http, properties.auth().refreshCookieName());
        AuthDtos.TokenBundle bundle = authService.refresh(raw, clientIp(http));
        return tokensResponse(bundle, clientType);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) AuthDtos.RefreshRequest request, HttpServletRequest http) {
        String raw = request != null && request.refreshToken() != null && !request.refreshToken().isBlank()
                ? request.refreshToken()
                : readRefreshCookie(http, properties.auth().refreshCookieName());
        authService.logout(raw);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll() {
        var accountId = JwtAuthFilter.currentAccountId();
        if (accountId != null) {
            authService.logoutAll(accountId);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<AuthDtos.MessageResponse> requestPasswordReset(
            @Valid @RequestBody AuthDtos.PasswordResetRequest request, HttpServletRequest http) {
        rateLimitService.check("pwreset", clientIp(http), 5, Duration.ofMinutes(1));
        authService.requestPasswordReset(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new AuthDtos.MessageResponse("If that account exists, a reset code is on its way"));
    }

    @PostMapping("/password-reset/confirm")
    public AuthDtos.MessageResponse confirmPasswordReset(
            @Valid @RequestBody AuthDtos.PasswordResetConfirmRequest request, HttpServletRequest http) {
        rateLimitService.check("pwreset", clientIp(http), 5, Duration.ofMinutes(1));
        authService.confirmPasswordReset(request);
        return new AuthDtos.MessageResponse("Password updated — log in with your new password");
    }

    @PostMapping("/roles/add")
    public AuthDtos.RolesResponse addRole(@Valid @RequestBody AuthDtos.AddRoleRequest request) {
        var accountId = JwtAuthFilter.currentAccountId();
        if (accountId == null) {
            throw com.wedjan.api.common.ApiException.unauthorized("AUTH_REQUIRED", "Authentication required");
        }
        return authService.addRole(accountId, request.role());
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private ResponseEntity<AuthDtos.AuthTokensResponse> tokensResponse(
            AuthDtos.TokenBundle bundle, String clientType) {
        boolean mobile = MOBILE.equalsIgnoreCase(clientType);
        AuthDtos.AuthTokensResponse body = new AuthDtos.AuthTokensResponse(
                bundle.accessToken(), bundle.expiresInSeconds(),
                mobile ? bundle.rawRefreshToken() : null, bundle.me());
        if (mobile) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(bundle.rawRefreshToken()).toString())
                .body(body);
    }

    private ResponseCookie refreshCookie(String rawToken) {
        return ResponseCookie.from(properties.auth().refreshCookieName(), rawToken)
                .httpOnly(true)
                .secure(properties.auth().refreshCookieSecure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(properties.auth().refreshTokenTtlDays()))
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(properties.auth().refreshCookieName(), "")
                .httpOnly(true)
                .secure(properties.auth().refreshCookieSecure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }

    public static String readRefreshCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
