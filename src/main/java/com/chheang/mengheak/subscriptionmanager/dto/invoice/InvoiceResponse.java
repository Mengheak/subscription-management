package com.chheang.mengheak.subscriptionmanager.dto.invoice;

import com.chheang.mengheak.subscriptionmanager.common.enums.InvoiceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "InvoiceResponse", description = "A billing document for one subscription period")
public record InvoiceResponse(

        UUID id,

        UUID subscriptionId,

        @Schema(example = "INV-000042")
        String invoiceNumber,

        InvoiceStatus status,

        @Schema(example = "USD")
        String currency,

        BigDecimal subtotal,

        BigDecimal taxAmount,

        BigDecimal total,

        BigDecimal amountPaid,

        @Schema(description = "total - amountPaid", example = "99.0000")
        BigDecimal amountDue,

        Instant periodStart,

        Instant periodEnd,

        Instant issuedAt,

        Instant dueAt,

        Instant paidAt,

        List<InvoiceLineItemResponse> lineItems,

        Instant createdAt
) {
}
