package com.chheang.mengheak.subscriptionmanager.security;

import com.chheang.mengheak.subscriptionmanager.common.enums.UserRole;
import com.chheang.mengheak.subscriptionmanager.config.properties.SecurityProperties;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-06T10:00:00Z");
    private static final String SECRET = "a-development-secret-that-is-long-enough-for-hs256";

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @Test
    @DisplayName("issues a token whose claims rebuild the same principal")
    void roundTripsThePrincipal() {
        JwtService service = jwtService(NOW, SECRET);

        JwtService.IssuedToken issued = service.issue(principal());
        AppUserPrincipal parsed = service.parse(issued.token());

        assertThat(parsed.userId()).isEqualTo(userId);
        assertThat(parsed.tenantId()).isEqualTo(tenantId);
        assertThat(parsed.email()).isEqualTo("admin@acme.example");
        assertThat(parsed.role()).isEqualTo(UserRole.TENANT_ADMIN);
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("never carries the password hash into a parsed principal")
    void parsedPrincipalHasNoCredentials() {
        JwtService service = jwtService(NOW, SECRET);

        AppUserPrincipal parsed = service.parse(service.issue(principal()).token());

        assertThat(parsed.passwordHash()).isNull();
        assertThat(parsed.getPassword()).isNull();
    }

    @Test
    @DisplayName("rejects a token signed with a different key")
    void rejectsForeignSignature() {
        String token = jwtService(NOW, SECRET).issue(principal()).token();
        JwtService otherDeployment = jwtService(NOW, "a-completely-different-secret-of-sufficient-length");

        assertThatThrownBy(() -> otherDeployment.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("rejects a token whose payload was altered")
    void rejectsTamperedPayload() {
        JwtService service = jwtService(NOW, SECRET);
        String token = service.issue(principal()).token();

        String[] parts = token.split("\\.");
        char last = parts[1].charAt(parts[1].length() - 1);
        String tampered = parts[0] + "."
                + parts[1].substring(0, parts[1].length() - 1) + (last == 'A' ? 'B' : 'A') + "."
                + parts[2];

        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("rejects a token that has expired beyond the configured skew")
    void rejectsExpiredToken() {
        String token = jwtService(NOW, SECRET).issue(principal()).token();

        JwtService later = jwtService(NOW.plus(Duration.ofHours(2)), SECRET);

        assertThatThrownBy(() -> later.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("refuses to start when the signing key is too weak for HS256")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> jwtService(NOW, "too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    private JwtService jwtService(Instant now, String secret) {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt(
                        secret,
                        "subscription-manager",
                        Duration.ofMinutes(30),
                        Duration.ofSeconds(30),
                        "tid",
                        "roles"),
                List.of("/api/v1/auth/**"));

        return new JwtService(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private AppUserPrincipal principal() {
        return new AppUserPrincipal(userId, tenantId, "admin@acme.example",
                "$2a$12$ignored", UserRole.TENANT_ADMIN, true);
    }
}
