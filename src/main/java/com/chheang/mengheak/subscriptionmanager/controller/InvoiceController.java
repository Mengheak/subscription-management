package com.chheang.mengheak.subscriptionmanager.controller;

import com.chheang.mengheak.subscriptionmanager.common.ApiResponse;
import com.chheang.mengheak.subscriptionmanager.common.constant.ApiPaths;
import com.chheang.mengheak.subscriptionmanager.common.enums.InvoiceStatus;
import com.chheang.mengheak.subscriptionmanager.common.PageResponse;
import com.chheang.mengheak.subscriptionmanager.dto.invoice.InvoiceResponse;
import com.chheang.mengheak.subscriptionmanager.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.INVOICES)
@RequiredArgsConstructor
@Tag(name = "Invoices", description = "Billing documents for the calling tenant")
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping("/generate")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(
            summary = "Invoice the current period",
            description = """
                    Bills the subscription's current period: the recurring plan fee plus any metered
                    overage accumulated from the usage topic.

                    The call is guarded against double-billing - a non-void invoice already covering
                    the same period start is rejected with 409 rather than duplicated.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Invoice issued"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "The period is already invoiced", content = @Content)
    })
    public ResponseEntity<ApiResponse<InvoiceResponse>> generate(
            @Parameter(description = "Subscription to bill", required = true)
            @RequestParam UUID subscriptionId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(invoiceService.generateForCurrentPeriod(subscriptionId)));
    }

    @GetMapping("/{invoiceId}")
    @Operation(summary = "Get one invoice, including its line items")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found in this tenant", content = @Content)
    public ResponseEntity<ApiResponse<InvoiceResponse>> get(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(ApiResponse.ok(invoiceService.get(invoiceId)));
    }

    @GetMapping
    @Operation(
            summary = "List invoices",
            description = "Line items are omitted from list results; fetch a single invoice to see them.")
    public ResponseEntity<ApiResponse<PageResponse<InvoiceResponse>>> list(
            @Parameter(description = "Optional status filter") @RequestParam(required = false) InvoiceStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(invoiceService.list(status, pageable)));
    }

    @PostMapping("/{invoiceId}/pay")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(
            summary = "Mark an invoice as settled",
            description = """
                    Records full settlement. Capturing the payment itself belongs to a payment
                    gateway integration; this endpoint only records the resulting state.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invoice marked paid"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found in this tenant", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Invoice is already in a terminal state", content = @Content)
    })
    public ResponseEntity<ApiResponse<InvoiceResponse>> markPaid(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(ApiResponse.ok(invoiceService.markPaid(invoiceId)));
    }
}
