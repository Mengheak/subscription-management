package com.chheang.mengheak.subscriptionmanager.dto.subscription;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "CreateSubscriptionRequest", description = "Subscribes the calling tenant to a plan")
public record CreateSubscriptionRequest(

        @Schema(description = "Plan catalogue code", example = "PRO")
        @NotBlank
        String planCode,

        @Schema(description = "Multiplier applied to the base price, e.g. seats", example = "1")
        @Min(1) @Max(100_000)
        int quantity,

        @Schema(description = "Skip the plan's free trial and start billing immediately", example = "false")
        boolean skipTrial
) {
}
