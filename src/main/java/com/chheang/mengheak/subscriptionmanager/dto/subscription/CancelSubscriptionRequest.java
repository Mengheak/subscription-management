package com.chheang.mengheak.subscriptionmanager.dto.subscription;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "CancelSubscriptionRequest", description = "Cancels a subscription now or at period end")
public record CancelSubscriptionRequest(

        @Schema(description = """
                When true the subscription stays live until the current period ends and is
                simply not renewed - the customer keeps what they already paid for.
                When false service stops immediately.""",
                example = "true")
        boolean atPeriodEnd,

        @Schema(description = "Free-text reason retained for churn analysis", example = "Migrating to annual plan")
        @Size(max = 255)
        String reason
) {
}
