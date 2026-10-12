package com.chheang.mengheak.subscriptionmanager.security;

import com.chheang.mengheak.subscriptionmanager.common.constant.SecurityConstants;
import com.chheang.mengheak.subscriptionmanager.common.enums.UserRole;
import com.chheang.mengheak.subscriptionmanager.config.properties.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;

@Slf4j
@Service
public class JwtService {

    private static final int MINIMUM_KEY_BYTES = 32;

    private final SecurityProperties.Jwt properties;
    private final SecretKey signingKey;
    private final Clock clock;

    private final io.jsonwebtoken.Clock jwtClock;

    public JwtService(SecurityProperties securityProperties, Clock clock) {
        this.properties = securityProperties.jwt();
        this.clock = clock;
        this.jwtClock = () -> Date.from(clock.instant());
        this.signingKey = buildSigningKey(this.properties.secret());
    }

    public IssuedToken issue(AppUserPrincipal user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());

        String token = Jwts.builder()
                .subject(user.userId().toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .claim(properties.tenantClaim(), user.tenantId().toString())
                .claim(properties.rolesClaim(), List.of(user.role().name()))
                .claim(SecurityConstants.CLAIM_EMAIL, user.email())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new IssuedToken(token, expiresAt);
    }

    public AppUserPrincipal parse(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .clock(jwtClock)
                .clockSkewSeconds(properties.clockSkew().toSeconds())
                .build()
                .parseSignedClaims(token);

        Claims claims = jws.getPayload();
        UUID userId = UUID.fromString(claims.getSubject());
        UUID tenantId = UUID.fromString(claims.get(properties.tenantClaim(), String.class));
        String email = claims.get(SecurityConstants.CLAIM_EMAIL, String.class);
        UserRole role = resolveRole(claims);

        return AppUserPrincipal.fromToken(userId, tenantId, email, role);
    }

    private UserRole resolveRole(Claims claims) {
        List<?> roles = claims.get(properties.rolesClaim(), List.class);
        if (roles == null || roles.isEmpty()) {
            throw new JwtException("Token carries no role claim");
        }
        return UserRole.valueOf(String.valueOf(roles.get(0)));
    }

    private static SecretKey buildSigningKey(String secret) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (DecodingException ex) {
            log.debug("JWT secret is not Base64; using its raw UTF-8 bytes");
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must provide at least %d bytes of key material for HS256, got %d"
                            .formatted(MINIMUM_KEY_BYTES, keyBytes.length));
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
