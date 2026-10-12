package com.chheang.mengheak.subscriptionmanager.dto.usage;

import com.chheang.mengheak.subscriptionmanager.common.enums.UsageMetric;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "UsageAcceptedResponse", description = "A usage event accepted for asynchronous processing")
public record UsageAcceptedResponse(

        @Schema(description = "Subscription the usage was attributed to")
        UUID subscriptionId,

        UsageMetric metric,

        BigDecimal quantity,

        Instant occurredAt,

        @Schema(description = "Key that makes a client retry safe", example = "gateway-2026-09-06T10:15:00Z-0042")
        String idempotencyKey,

        @Schema(description = "Topic the event was published to", example = "billing.usage-events.v1")
        String topic,

        @Schema(description = "Partition the broker assigned", example = "3")
        int partition,

        @Schema(description = "Offset within the partition", example = "18422")
        long offset
) {
}
