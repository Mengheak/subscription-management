package com.chheang.mengheak.subscriptionmanager.dto.plan;

import com.chheang.mengheak.subscriptionmanager.common.enums.UsageMetric;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "MeteredRateResponse", description = "Included allowance and overage price for one metric")
public record MeteredRateResponse(

        UsageMetric metric,

        @Schema(description = "Quantity covered by the base fee", example = "50000.0000")
        BigDecimal includedQuantity,

        @Schema(description = "Price per unit beyond the included quantity", example = "0.000200")
        BigDecimal unitPrice
) {
}
