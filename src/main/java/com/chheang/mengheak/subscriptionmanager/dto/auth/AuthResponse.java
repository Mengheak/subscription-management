package com.chheang.mengheak.subscriptionmanager.dto.auth;

import com.chheang.mengheak.subscriptionmanager.common.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "AuthResponse", description = "A newly issued access token")
public record AuthResponse(

        @Schema(description = "Signed JWT to send as 'Authorization: Bearer <token>'")
        String accessToken,

        @Schema(description = "Token scheme", example = "Bearer")
        String tokenType,

        @Schema(description = "Absolute expiry of the access token")
        Instant expiresAt,

        @Schema(description = "Authenticated user id")
        UUID userId,

        @Schema(description = "Tenant the user belongs to")
        UUID tenantId,

        @Schema(description = "Tenant handle", example = "acme-corp")
        String tenantSlug,

        @Schema(description = "Role granted inside the tenant")
        UserRole role
) {
}
