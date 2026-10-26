package com.chheang.mengheak.subscriptionmanager.dto.merchant;

import com.chheang.mengheak.subscriptionmanager.common.enums.BillingCycle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Public merchant contracts. Catalog prices are immutable; archive and create a new price to change
 * pricing.
 */
public final class MerchantDtos {
  private MerchantDtos() {}

  public record ProductRequest(
      @NotBlank @Size(max = 120) String name, @Size(max = 500) String description) {}

  public record ProductResponse(
      UUID id, String name, String description, boolean active, Instant createdAt) {}

  public record MeteredRate(
      @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,59}") String metric,
      @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal includedQuantity,
      @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 6) BigDecimal unitPrice) {}

  public record PriceRequest(
      @NotNull UUID productId,
      @NotBlank @Size(max = 120) String name,
      @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal amount,
      @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotNull BillingCycle billingCycle,
      @Min(0) @Max(365) int trialDays,
      @NotNull @Size(max = 50)
          Set<@NotBlank @Pattern(regexp = "[a-z][a-z0-9_.-]{0,59}") String> features,
      @NotNull @Size(max = 20) List<@Valid MeteredRate> meteredRates) {}

  public record PriceResponse(
      UUID id,
      UUID productId,
      String name,
      BigDecimal amount,
      String currency,
      BillingCycle billingCycle,
      int trialDays,
      boolean active,
      Set<String> features,
      List<MeteredRate> meteredRates) {}

  public record CustomerRequest(
      @NotBlank @Size(max = 120) String externalId,
      @NotBlank @Size(max = 150) String name,
      @NotBlank @Email @Size(max = 255) String email) {}

  public record CustomerUpdate(
      @NotBlank @Size(max = 150) String name,
      @NotBlank @Email @Size(max = 255) String email,
      boolean active) {}

  public record CustomerResponse(
      UUID id, String externalId, String name, String email, boolean active, Instant createdAt) {}

  public record SubscriptionRequest(
      @NotNull UUID customerId,
      @NotNull UUID priceId,
      @Min(1) @Max(100000) int quantity,
      boolean skipTrial,
      @NotBlank @Size(max = 120) String idempotencyKey) {}

  public record SubscriptionResponse(
      UUID id,
      UUID customerId,
      String customerName,
      String customerExternalId,
      UUID productId,
      String productName,
      UUID priceId,
      String priceName,
      BigDecimal amount,
      String currency,
      BillingCycle billingCycle,
      int quantity,
      String status,
      Instant periodStart,
      Instant periodEnd,
      boolean cancelAtPeriodEnd) {}

  public record CancelRequest(boolean atPeriodEnd) {}

  public record UsageRequest(
      @NotNull UUID subscriptionId,
      @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,59}") String metric,
      @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal quantity,
      @NotBlank @Size(max = 120) String idempotencyKey) {}

  public record UsageAccepted(
      UUID id,
      UUID subscriptionId,
      String metric,
      BigDecimal quantity,
      String idempotencyKey,
      Instant occurredAt) {}

  public record UsageMetric(
      String metric,
      BigDecimal totalQuantity,
      BigDecimal includedQuantity,
      BigDecimal overageQuantity,
      BigDecimal unitPrice,
      BigDecimal estimatedCost) {}

  public record UsageResponse(
      UUID subscriptionId,
      String currency,
      Instant periodStart,
      Instant periodEnd,
      List<UsageMetric> metrics,
      BigDecimal estimatedOverageTotal) {}

  public record AccessResponse(
      boolean allowed,
      String reason,
      UUID subscriptionId,
      Set<String> features,
      Instant validUntil) {}

  public record InvoiceResponse(
      UUID id,
      UUID subscriptionId,
      UUID customerId,
      String customerName,
      String invoiceNumber,
      String kind,
      String currency,
      BigDecimal baseAmount,
      BigDecimal usageAmount,
      BigDecimal total,
      BigDecimal amountPaid,
      String status,
      Instant periodStart,
      Instant periodEnd,
      Instant dueAt,
      Instant paidAt,
      Instant createdAt) {}

  public record KeyRequest(
      @NotBlank @Size(max = 120) String name,
      @NotEmpty @Size(max = 9) Set<String> scopes,
      @Min(1) @Max(365) int expiresInDays) {}

  public record KeyResponse(
      UUID id,
      String name,
      String prefix,
      Set<String> scopes,
      String status,
      Instant expiresAt,
      Instant revokedAt,
      Instant createdAt) {}

  public record CreatedKey(KeyResponse key, String secret) {}

  public record Onboarding(
      boolean unlocked,
      String reason,
      long products,
      long prices,
      long customers,
      long subscriptions,
      long apiKeys) {}
}
