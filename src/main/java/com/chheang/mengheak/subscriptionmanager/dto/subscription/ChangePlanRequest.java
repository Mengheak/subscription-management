package com.chheang.mengheak.subscriptionmanager.dto.subscription;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ChangePlanRequest", description = "Moves a subscription to a different plan and/or quantity")
public record ChangePlanRequest(

        @Schema(description = "Target plan catalogue code", example = "ENTERPRISE")
        @NotBlank
        String planCode,

        @Schema(description = "New quantity; omit to keep the current one", example = "5")
        @Min(1) @Max(100_000)
        Integer quantity,

        @Schema(description = """
                Overrides the app.billing.proration-enabled default for this single change.
                Omit to use the configured behaviour.""")
        Boolean prorate
) {
}
