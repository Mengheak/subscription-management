package com.chheang.mengheak.subscriptionmanager.dto.usage;

import com.chheang.mengheak.subscriptionmanager.common.enums.UsageMetric;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "UsageMetricSummary", description = "Consumption and projected cost for one metric")
public record UsageMetricSummary(

        UsageMetric metric,

        @Schema(description = "Consumption recorded so far in the current period", example = "612500.0000")
        BigDecimal totalQuantity,

        @Schema(description = "Quantity covered by the plan's base fee", example = "500000.0000")
        BigDecimal includedQuantity,

        @Schema(description = "max(0, total - included)", example = "112500.0000")
        BigDecimal overageQuantity,

        @Schema(description = "Overage price per unit", example = "0.000150")
        BigDecimal unitPrice,

        @Schema(description = "overageQuantity x unitPrice, rounded to the billing scale", example = "16.88")
        BigDecimal estimatedCost
) {
}
