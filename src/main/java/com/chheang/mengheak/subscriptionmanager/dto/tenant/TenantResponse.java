package com.chheang.mengheak.subscriptionmanager.dto.tenant;

import com.chheang.mengheak.subscriptionmanager.common.enums.TenantStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "TenantResponse", description = "A customer organisation")
public record TenantResponse(

        UUID id,

        @Schema(example = "Acme Corporation")
        String name,

        @Schema(example = "acme-corp")
        String slug,

        @Schema(example = "admin@acme.example")
        String contactEmail,

        TenantStatus status,

        Instant createdAt
) {
}
