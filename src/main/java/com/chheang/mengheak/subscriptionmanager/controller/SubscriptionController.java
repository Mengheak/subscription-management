package com.chheang.mengheak.subscriptionmanager.controller;

import com.chheang.mengheak.subscriptionmanager.common.ApiResponse;
import com.chheang.mengheak.subscriptionmanager.common.constant.ApiPaths;
import com.chheang.mengheak.subscriptionmanager.common.PageResponse;
import com.chheang.mengheak.subscriptionmanager.dto.subscription.CancelSubscriptionRequest;
import com.chheang.mengheak.subscriptionmanager.dto.subscription.ChangePlanRequest;
import com.chheang.mengheak.subscriptionmanager.dto.subscription.CreateSubscriptionRequest;
import com.chheang.mengheak.subscriptionmanager.dto.subscription.SubscriptionResponse;
import com.chheang.mengheak.subscriptionmanager.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping(ApiPaths.SUBSCRIPTIONS)
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "Create, change and cancel subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(
            summary = "Subscribe to a plan",
            description = """
                    Starts a subscription for the calling tenant. If the plan offers a trial and
                    `skipTrial` is false, the subscription starts in `TRIALING` and the first
                    period ends when the trial does.

                    A tenant may hold only one live subscription at a time.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Subscription created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan not found or inactive", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "The tenant already has a live subscription", content = @Content)
    })
    public ResponseEntity<ApiResponse<SubscriptionResponse>> create(@Valid @RequestBody CreateSubscriptionRequest request) {
        SubscriptionResponse created = subscriptionService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath(ApiPaths.SUBSCRIPTIONS + "/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(ApiResponse.ok(created));
    }

    @PostMapping("/{subscriptionId}/change-plan")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(
            summary = "Upgrade or downgrade",
            description = """
                    Moves the subscription to another plan and/or quantity.

                    When proration applies, a settlement invoice covering only the remainder of the
                    current period is issued immediately: a credit for the unused part of the old
                    plan and a charge for the same window on the new one. The period boundaries
                    themselves do not move - a different billing cycle takes effect at renewal.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Plan changed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription or plan not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Subscription is not live, or nothing would change", content = @Content)
    })
    public ResponseEntity<ApiResponse<SubscriptionResponse>> changePlan(@PathVariable UUID subscriptionId,
                                                          @Valid @RequestBody ChangePlanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.changePlan(subscriptionId, request)));
    }

    @PostMapping("/{subscriptionId}/cancel")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(
            summary = "Cancel a subscription",
            description = """
                    With `atPeriodEnd = true` the subscription stays live until the current period
                    ends and is simply not renewed. With `false` service stops immediately; no
                    credit note is produced for the unused remainder.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cancellation recorded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Subscription is already ended", content = @Content)
    })
    public ResponseEntity<ApiResponse<SubscriptionResponse>> cancel(@PathVariable UUID subscriptionId,
                                                       @Valid @RequestBody CancelSubscriptionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.cancel(subscriptionId, request)));
    }

    @GetMapping("/{subscriptionId}")
    @Operation(summary = "Get one subscription")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found in this tenant", content = @Content)
    public ResponseEntity<ApiResponse<SubscriptionResponse>> get(@PathVariable UUID subscriptionId) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.get(subscriptionId)));
    }

    @GetMapping
    @Operation(summary = "List the tenant's subscriptions, current and historical")
    public ResponseEntity<ApiResponse<PageResponse<SubscriptionResponse>>> list(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.list(pageable)));
    }
}
