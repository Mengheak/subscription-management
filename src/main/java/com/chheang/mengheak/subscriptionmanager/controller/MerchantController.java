package com.chheang.mengheak.subscriptionmanager.controller;

import com.chheang.mengheak.subscriptionmanager.common.*;
import com.chheang.mengheak.subscriptionmanager.dto.merchant.MerchantDtos.*;
import com.chheang.mengheak.subscriptionmanager.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchant")
@RequiredArgsConstructor
@Validated
@Tag(
    name = "Merchant billing",
    description =
        "Your own catalog, customers, subscriptions, usage and invoices. Requires an active"
            + " platform subscription.")
@SecurityRequirements({
  @SecurityRequirement(name = "bearerAuth"),
  @SecurityRequirement(name = "apiKeyAuth")
})
public class MerchantController {
  private final MerchantService service;
  private static final String HUMAN = "hasAnyRole('TENANT_ADMIN','TENANT_MEMBER')";
  private static final String ADMIN = "hasRole('TENANT_ADMIN')";

  @GetMapping("/onboarding")
  @PreAuthorize(HUMAN)
  public ApiResponse<Onboarding> onboarding() {
    return ApiResponse.ok(service.onboarding());
  }

  @GetMapping("/products")
  @PreAuthorize(HUMAN + " or hasAuthority('SCOPE_CATALOG_READ')")
  public ApiResponse<PageResponse<ProductResponse>> products(
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable page) {
    return ApiResponse.ok(service.products(page));
  }

  @GetMapping("/products/{id}")
  @PreAuthorize(HUMAN + " or hasAuthority('SCOPE_CATALOG_READ')")
  public ApiResponse<ProductResponse> product(@PathVariable UUID id) {
    return ApiResponse.ok(service.product(id));
  }

  @PostMapping("/products")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(ADMIN)
  public ApiResponse<ProductResponse> createProduct(@Valid @RequestBody ProductRequest r) {
    return ApiResponse.ok(service.createProduct(r));
  }

  @PostMapping("/products/{id}/archive")
  @PreAuthorize(ADMIN)
  public ApiResponse<ProductResponse> archiveProduct(@PathVariable UUID id) {
    return ApiResponse.ok(service.archiveProduct(id));
  }

  @GetMapping("/prices")
  @PreAuthorize(HUMAN + " or hasAuthority('SCOPE_CATALOG_READ')")
  public ApiResponse<PageResponse<PriceResponse>> prices(
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable page) {
    return ApiResponse.ok(service.prices(page));
  }

  @PostMapping("/prices")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(ADMIN)
  public ApiResponse<PriceResponse> createPrice(@Valid @RequestBody PriceRequest r) {
    return ApiResponse.ok(service.createPrice(r));
  }

  @PostMapping("/prices/{id}/archive")
  @PreAuthorize(ADMIN)
  public ApiResponse<PriceResponse> archivePrice(@PathVariable UUID id) {
    return ApiResponse.ok(service.archivePrice(id));
  }

  @GetMapping("/customers")
  @PreAuthorize(HUMAN + " or hasAuthority('SCOPE_CUSTOMERS_READ')")
  public ApiResponse<PageResponse<CustomerResponse>> customers(
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable page) {
    return ApiResponse.ok(service.customers(page));
  }

  @GetMapping("/customers/{id}")
  @PreAuthorize(HUMAN + " or hasAuthority('SCOPE_CUSTOMERS_READ')")
  public ApiResponse<CustomerResponse> customer(@PathVariable UUID id) {
    return ApiResponse.ok(service.customer(id));
  }

  @GetMapping("/customers/lookup")
  @PreAuthorize(HUMAN + " or hasAuthority('SCOPE_CUSTOMERS_READ')")
  public ApiResponse<CustomerResponse> lookup(
      @RequestParam @NotBlank @Size(max = 120) String externalId) {
    return ApiResponse.ok(service.customerByExternalId(externalId));
  }

  @PostMapping("/customers")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(ADMIN + " or hasAuthority('SCOPE_CUSTOMERS_WRITE')")
  public ApiResponse<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest r) {
    return ApiResponse.ok(service.createCustomer(r));
  }

  @PutMapping("/customers/{id}")
  @PreAuthorize(ADMIN + " or hasAuthority('SCOPE_CUSTOMERS_WRITE')")
  public ApiResponse<CustomerResponse> updateCustomer(
      @PathVariable UUID id, @Valid @RequestBody CustomerUpdate r) {
    return ApiResponse.ok(service.updateCustomer(id, r));
  }

  @GetMapping("/subscriptions")
  @PreAuthorize(HUMAN + " or hasAuthority('SCOPE_SUBSCRIPTIONS_READ')")
  public ApiResponse<PageResponse<SubscriptionResponse>> subscriptions(
      @RequestParam(required = false) UUID customerId,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable page) {
    return ApiResponse.ok(service.subscriptions(customerId, page));
  }

  @GetMapping("/subscriptions/{id}")
  @PreAuthorize(HUMAN + " or hasAuthority('SCOPE_SUBSCRIPTIONS_READ')")
  public ApiResponse<SubscriptionResponse> subscription(@PathVariable UUID id) {
    return ApiResponse.ok(service.subscription(id));
  }

  @PostMapping("/subscriptions")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize(ADMIN + " or hasAuthority('SCOPE_SUBSCRIPTIONS_WRITE')")
  @Operation(
      summary = "Subscribe one of your customers",
      description =
          "Idempotency key is required. Creates an upfront BASE invoice unless the customer starts"
              + " a trial. One live subscription per customer/product.")
  public ApiResponse<SubscriptionResponse> subscribe(@Valid @RequestBody SubscriptionRequest r) {
    return ApiResponse.ok(service.subscribe(r));
  }

  @PostMapping("/subscriptions/{id}/cancel")
  @PreAuthorize(ADMIN + " or hasAuthority('SCOPE_SUBSCRIPTIONS_WRITE')")
  public ApiResponse<SubscriptionResponse> cancel(
      @PathVariable UUID id, @Valid @RequestBody CancelRequest r) {
    return ApiResponse.ok(service.cancel(id, r));
  }

  @GetMapping("/access")
  @PreAuthorize(HUMAN + " or hasAuthority('SCOPE_ACCESS_READ')")
  @Operation(
      summary = "Check customer access",
      description =
          "Call from your backend before granting a product feature. Checks company plan, customer,"
              + " subscription dates, overdue invoices and the price's features.")
  public ApiResponse<AccessResponse> access(
      @RequestParam @NotBlank @Size(max = 120) String customerExternalId,
      @RequestParam UUID productId,
      @RequestParam(required = false) @Size(max = 60) String feature) {
    return ApiResponse.ok(service.access(customerExternalId, productId, feature));
  }

  @PostMapping("/usage/events")
  @PreAuthorize(ADMIN + " or hasAuthority('SCOPE_USAGE_WRITE')")
  @Operation(
      summary = "Record customer usage",
      description =
          "Synchronously persists usage under a tenant-unique idempotency key. Retries with a"
              + " changed payload fail. Usage is attributed to the server's current time.")
  public ApiResponse<UsageAccepted> recordUsage(@Valid @RequestBody UsageRequest r) {
    return ApiResponse.ok(service.recordUsage(r));
  }

  @GetMapping("/subscriptions/{id}/usage")
  @PreAuthorize(HUMAN + " or hasAuthority('SCOPE_SUBSCRIPTIONS_READ')")
  public ApiResponse<UsageResponse> usage(@PathVariable UUID id) {
    return ApiResponse.ok(service.usage(id));
  }

  @GetMapping("/invoices")
  @PreAuthorize(HUMAN + " or hasAuthority('SCOPE_INVOICES_READ')")
  public ApiResponse<PageResponse<InvoiceResponse>> invoices(
      @RequestParam(required = false) UUID customerId,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable page) {
    return ApiResponse.ok(service.invoices(customerId, page));
  }

  @GetMapping("/invoices/{id}")
  @PreAuthorize(HUMAN + " or hasAuthority('SCOPE_INVOICES_READ')")
  public ApiResponse<InvoiceResponse> invoice(@PathVariable UUID id) {
    return ApiResponse.ok(service.invoice(id));
  }

  @PostMapping("/invoices/{id}/pay")
  @PreAuthorize(ADMIN)
  @Operation(
      summary = "Record manual settlement",
      description =
          "Administrator-only. Records full payment received elsewhere; does not charge a card. API"
              + " keys cannot settle invoices.")
  public ApiResponse<InvoiceResponse> pay(@PathVariable UUID id) {
    return ApiResponse.ok(service.markPaid(id));
  }
}
