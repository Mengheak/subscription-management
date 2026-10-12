package com.chheang.mengheak.subscriptionmanager.messaging.event;

import com.chheang.mengheak.subscriptionmanager.common.enums.UsageMetric;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UsageEventMessage(
        UUID tenantId,
        UUID subscriptionId,
        UsageMetric metric,
        BigDecimal quantity,
        String idempotencyKey,
        String source,
        Instant occurredAt
) {

    public String partitionKey() {
        return tenantId + ":" + subscriptionId;
    }
}
