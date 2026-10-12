package com.chheang.mengheak.subscriptionmanager.controller;

import com.chheang.mengheak.subscriptionmanager.common.ApiResponse;
import com.chheang.mengheak.subscriptionmanager.common.constant.ApiPaths;
import com.chheang.mengheak.subscriptionmanager.dto.usage.RecordUsageRequest;
import com.chheang.mengheak.subscriptionmanager.dto.usage.UsageAcceptedResponse;
import com.chheang.mengheak.subscriptionmanager.dto.usage.UsageSummaryResponse;
import com.chheang.mengheak.subscriptionmanager.service.UsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.USAGE)
@RequiredArgsConstructor
@Tag(name = "Usage", description = "Report metered consumption and inspect the current cycle")
public class UsageController {

    private final UsageService usageService;

    @PostMapping("/events")
    @Operation(
            summary = "Report metered usage",
            description = """
                    Publishes the event to Kafka and returns **202 Accepted** once the broker has
                    durably acknowledged it. Counters are updated asynchronously by the consumer, so
                    the totals from the summary endpoint may lag this call by a moment.

                    Supply your own `idempotencyKey` if the caller may retry: it is unique per tenant,
                    so a duplicate delivery - or a duplicate request - is billed only once.

                    The subscription is resolved server-side from the token's tenant; it cannot be
                    chosen by the caller.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Event durably published"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "The tenant has no live subscription, or the timestamp is in the future", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "The broker did not acknowledge the write; retry with the same idempotency key", content = @Content)
    })
    public ResponseEntity<ApiResponse<UsageAcceptedResponse>> record(@Valid @RequestBody RecordUsageRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(usageService.record(request)));
    }

    @GetMapping("/subscriptions/{subscriptionId}/summary")
    @Operation(
            summary = "Current-period usage and projected overage",
            description = """
                    Shows every metric the plan prices, including those still inside their allowance,
                    with the cost the period would incur if it were invoiced right now.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found in this tenant", content = @Content)
    public ResponseEntity<ApiResponse<UsageSummaryResponse>> summary(@PathVariable UUID subscriptionId) {
        return ResponseEntity.ok(ApiResponse.ok(usageService.currentPeriodSummary(subscriptionId)));
    }
}
