package com.chheang.mengheak.subscriptionmanager.dto.invoice;

import com.chheang.mengheak.subscriptionmanager.common.enums.InvoiceLineType;
import com.chheang.mengheak.subscriptionmanager.common.enums.UsageMetric;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "InvoiceLineItemResponse", description = "One priced row of an invoice")
public record InvoiceLineItemResponse(

        UUID id,

        InvoiceLineType lineType,

        @Schema(example = "Professional - 2026-09-06 to 2026-10-06")
        String description,

        @Schema(description = "Present only on USAGE lines")
        UsageMetric metric,

        BigDecimal quantity,

        BigDecimal unitPrice,

        @Schema(description = "Rounded line total; negative for credits", example = "99.0000")
        BigDecimal amount,

        Instant periodStart,

        Instant periodEnd
) {
}
