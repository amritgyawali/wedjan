package com.wedjan.api.account;

import com.wedjan.api.auth.AuthController;
import com.wedjan.api.auth.AuthService;
import com.wedjan.api.config.JwtAuthFilter;
import com.wedjan.api.config.WedjanProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final AccountService accountService;
    private final AuthService authService;
    private final WedjanProperties properties;

    public MeController(AccountService accountService, AuthService authService, WedjanProperties properties) {
        this.accountService = accountService;
        this.authService = authService;
        this.properties = properties;
    }

    @GetMapping
    public AccountDtos.MeResponse me() {
        return accountService.me(JwtAuthFilter.currentAccountId());
    }

    @PatchMapping
    public AccountDtos.MeResponse updateMe(@Valid @RequestBody AccountDtos.UpdateProfileRequest request) {
        return accountService.updateMe(JwtAuthFilter.currentAccountId(), request);
    }

    @GetMapping("/sessions")
    public AccountDtos.SessionListResponse sessions(HttpServletRequest request) {
        String refreshCookie = AuthController.readRefreshCookie(
                request, properties.auth().refreshCookieName());
        return authService.listSessions(JwtAuthFilter.currentAccountId(), refreshCookie);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID sessionId) {
        authService.revokeSession(JwtAuthFilter.currentAccountId(), sessionId);
        return ResponseEntity.noContent().build();
    }
}
