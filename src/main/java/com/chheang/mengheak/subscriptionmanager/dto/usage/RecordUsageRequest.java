package com.chheang.mengheak.subscriptionmanager.dto.usage;

import com.chheang.mengheak.subscriptionmanager.common.enums.UsageMetric;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "RecordUsageRequest", description = "Reports metered consumption for the calling tenant")
public record RecordUsageRequest(

        @Schema(description = "Metered dimension", example = "API_CALLS")
        @NotNull
        UsageMetric metric,

        @Schema(description = "Consumed amount; must be non-negative", example = "1250.0000")
        @NotNull @DecimalMin(value = "0.0") @Digits(integer = 15, fraction = 4)
        BigDecimal quantity,

        @Schema(description = """
                When the consumption happened. Defaults to now.
                This - not the ingest time - decides which billing period is charged.""")
        Instant occurredAt,

        @Schema(description = """
                Caller-supplied de-duplication key, unique per tenant. Omit and the server
                generates one, but then a client-side retry will be billed twice.""",
                example = "gateway-2026-09-06T10:15:00Z-0042")
        @Size(max = 120)
        String idempotencyKey,

        @Schema(description = "Origin marker retained for troubleshooting", example = "api-gateway")
        @Size(max = 60)
        String source
) {
}
