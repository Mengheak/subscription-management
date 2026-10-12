package com.chheang.mengheak.subscriptionmanager.dto.plan;

import com.chheang.mengheak.subscriptionmanager.common.enums.BillingCycle;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(name = "PlanResponse", description = "A priced tier from the platform catalogue")
public record PlanResponse(

        UUID id,

        @Schema(example = "PRO")
        String code,

        @Schema(example = "Professional")
        String name,

        String description,

        BillingCycle billingCycle,

        @Schema(description = "Recurring fee per unit of quantity, per cycle", example = "99.0000")
        BigDecimal basePrice,

        @Schema(example = "USD")
        String currency,

        @Schema(description = "Free trial length in days; 0 means no trial", example = "14")
        int trialDays,

        boolean active,

        List<MeteredRateResponse> meteredRates
) {
}
