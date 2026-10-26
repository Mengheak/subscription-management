package com.chheang.mengheak.subscriptionmanager.mapper;

import com.chheang.mengheak.subscriptionmanager.dto.merchant.MerchantDtos.*;
import com.chheang.mengheak.subscriptionmanager.entity.*;
import org.springframework.stereotype.Component;

@Component
@lombok.RequiredArgsConstructor
public class MerchantMapper {
  private final java.time.Clock clock;

  public ProductResponse product(MerchantProduct p) {
    return new ProductResponse(
        p.getId(), p.getName(), p.getDescription(), p.isActive(), p.getCreatedAt());
  }

  public PriceResponse price(MerchantPrice p) {
    return new PriceResponse(
        p.getId(),
        p.getProductId(),
        p.getName(),
        p.getAmount(),
        p.getCurrency(),
        p.getBillingCycle(),
        p.getTrialDays(),
        p.isActive(),
        p.getFeatures(),
        p.getMeteredRates());
  }

  public CustomerResponse customer(MerchantCustomer c) {
    return new CustomerResponse(
        c.getId(), c.getExternalId(), c.getName(), c.getEmail(), c.isActive(), c.getCreatedAt());
  }

  public SubscriptionResponse subscription(
      CustomerSubscription s, MerchantCustomer c, MerchantProduct p, MerchantPrice price) {
    return new SubscriptionResponse(
        s.getId(),
        c.getId(),
        c.getName(),
        c.getExternalId(),
        p.getId(),
        p.getName(),
        price.getId(),
        price.getName(),
        price.getAmount(),
        price.getCurrency(),
        price.getBillingCycle(),
        s.getQuantity(),
        s.getStatus(),
        s.getPeriodStart(),
        s.getPeriodEnd(),
        s.isCancelAtPeriodEnd());
  }

  public InvoiceResponse invoice(CustomerInvoice i, MerchantCustomer c) {
    return new InvoiceResponse(
        i.getId(),
        i.getSubscriptionId(),
        c.getId(),
        c.getName(),
        i.getInvoiceNumber(),
        i.getKind(),
        i.getCurrency(),
        i.getBaseAmount(),
        i.getUsageAmount(),
        i.getTotal(),
        i.getAmountPaid(),
        i.getStatus(),
        i.getPeriodStart(),
        i.getPeriodEnd(),
        i.getDueAt(),
        i.getPaidAt(),
        i.getCreatedAt());
  }

  public UsageAccepted event(CustomerUsageEvent e) {
    return new UsageAccepted(
        e.getId(),
        e.getSubscriptionId(),
        e.getMetric(),
        e.getQuantity(),
        e.getIdempotencyKey(),
        e.getOccurredAt());
  }

  public KeyResponse key(MerchantApiKey k) {
    return new KeyResponse(
        k.getId(),
        k.getName(),
        k.getPrefix(),
        k.getScopes(),
        k.getRevokedAt() != null
            ? "REVOKED"
            : k.getExpiresAt().isAfter(clock.instant()) ? "ACTIVE" : "EXPIRED",
        k.getExpiresAt(),
        k.getRevokedAt(),
        k.getCreatedAt());
  }
}
