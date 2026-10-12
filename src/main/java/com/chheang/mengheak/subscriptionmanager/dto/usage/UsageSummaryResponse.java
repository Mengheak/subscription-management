package com.chheang.mengheak.subscriptionmanager.dto.usage;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "UsageSummaryResponse", description = "Current-period usage and projected overage cost")
public record UsageSummaryResponse(

        UUID subscriptionId,

        @Schema(example = "PRO")
        String planCode,

        Instant periodStart,

        Instant periodEnd,

        @Schema(example = "USD")
        String currency,

        List<UsageMetricSummary> metrics,

        @Schema(description = "Sum of the per-metric estimated costs", example = "16.88")
        BigDecimal estimatedOverageTotal
) {
}
