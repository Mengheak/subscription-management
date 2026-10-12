package com.chheang.mengheak.subscriptionmanager.dto.subscription;

import com.chheang.mengheak.subscriptionmanager.common.enums.BillingCycle;
import com.chheang.mengheak.subscriptionmanager.common.enums.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "SubscriptionResponse", description = "A tenant's subscription and its current billing period")
public record SubscriptionResponse(

        UUID id,

        UUID tenantId,

        UUID planId,

        @Schema(example = "PRO")
        String planCode,

        @Schema(example = "Professional")
        String planName,

        BillingCycle billingCycle,

        @Schema(description = "Plan base price per unit of quantity", example = "99.0000")
        BigDecimal basePrice,

        @Schema(example = "USD")
        String currency,

        SubscriptionStatus status,

        int quantity,

        Instant startedAt,

        Instant currentPeriodStart,

        Instant currentPeriodEnd,

        @Schema(description = "End of the free trial, if any")
        Instant trialEnd,

        boolean cancelAtPeriodEnd,

        Instant canceledAt,

        Instant endedAt,

        Instant createdAt,

        Instant updatedAt
) {
}
