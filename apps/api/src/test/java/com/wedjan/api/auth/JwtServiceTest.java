package com.wedjan.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.wedjan.api.config.WedjanProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(new WedjanProperties(
            new WedjanProperties.Auth(
                    "test-secret-test-secret-test-secret-test-secret", 900, 30, 15, 5,
                    "wedjan_refresh", false),
            null, null, null, null));

    @Test
    void roundTripsAccessTokenClaims() {
        UUID accountId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(accountId, "a@b.co", List.of("CUSTOMER", "VENDOR"));

        JwtService.AccessTokenClaims claims = jwtService.parseAccessToken(token);

        assertThat(claims).isNotNull();
        assertThat(claims.accountId()).isEqualTo(accountId);
        assertThat(claims.email()).isEqualTo("a@b.co");
        assertThat(claims.roles()).containsExactly("CUSTOMER", "VENDOR");
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), "a@b.co", List.of("CUSTOMER"));
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThat(jwtService.parseAccessToken(tampered)).isNull();
    }

    @Test
    void rejectsGarbage() {
        assertThat(jwtService.parseAccessToken("not-a-jwt")).isNull();
    }
}
