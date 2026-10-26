package com.chheang.mengheak.subscriptionmanager.service.impl;

import com.chheang.mengheak.subscriptionmanager.common.PageResponse;
import com.chheang.mengheak.subscriptionmanager.dto.merchant.MerchantDtos.*;
import com.chheang.mengheak.subscriptionmanager.entity.*;
import com.chheang.mengheak.subscriptionmanager.exception.*;
import com.chheang.mengheak.subscriptionmanager.mapper.MerchantMapper;
import com.chheang.mengheak.subscriptionmanager.repository.*;
import com.chheang.mengheak.subscriptionmanager.security.TenantContextHolder;
import com.chheang.mengheak.subscriptionmanager.service.*;
import java.math.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantServiceImpl implements MerchantService {
  private static final List<String> LIVE = List.of("ACTIVE", "TRIALING", "PAST_DUE");
  private final MerchantProductRepository products;
  private final MerchantPriceRepository prices;
  private final MerchantCustomerRepository customers;
  private final CustomerSubscriptionRepository subscriptions;
  private final CustomerInvoiceRepository invoices;
  private final CustomerUsageEventRepository events;
  private final MerchantApiKeyRepository keys;
  private final MerchantMapper mapper;
  private final MerchantAccess gate;
  private final Clock clock;

  @Override
  public Onboarding onboarding() {
    UUID t = TenantContextHolder.requireTenantId();
    boolean unlocked = gate.unlocked(t);
    return new Onboarding(
        unlocked,
        unlocked
            ? "Ready to build your billing integration"
            : "Subscribe to a platform plan to unlock merchant tools",
        products.countByTenantId(t),
        prices.countByTenantIdAndActiveTrue(t),
        customers.countByTenantId(t),
        subscriptions.countByTenantId(t),
        keys.countByTenantIdAndRevokedAtIsNullAndExpiresAtAfter(t, clock.instant()));
  }

  @Override
  public PageResponse<ProductResponse> products(Pageable page) {
    return PageResponse.from(products.findAllByTenantId(gate.require(), page), mapper::product);
  }

  @Override
  public ProductResponse product(UUID id) {
    return mapper.product(productEntity(id, gate.require()));
  }

  @Override
  @Transactional
  public ProductResponse createProduct(ProductRequest r) {
    UUID t = gate.lock();
    return mapper.product(
        products.save(
            MerchantProduct.builder()
                .tenantId(t)
                .name(r.name().trim())
                .description(r.description())
                .active(true)
                .build()));
  }

  @Override
  @Transactional
  public ProductResponse archiveProduct(UUID id) {
    MerchantProduct p = productEntity(id, gate.lock());
    p.setActive(false);
    return mapper.product(p);
  }

  @Override
  public PageResponse<PriceResponse> prices(Pageable page) {
    return PageResponse.from(prices.findAllByTenantId(gate.require(), page), mapper::price);
  }

  @Override
  @Transactional
  public PriceResponse createPrice(PriceRequest r) {
    UUID t = gate.lock();
    if (!productEntity(r.productId(), t).isActive())
      throw rule("PRODUCT_ARCHIVED", "Create prices only for active products");
    if (r.meteredRates().stream().map(MeteredRate::metric).distinct().count()
        != r.meteredRates().size())
      throw rule("DUPLICATE_METRIC", "Each metric may appear only once");
    return mapper.price(
        prices.save(
            MerchantPrice.builder()
                .tenantId(t)
                .productId(r.productId())
                .name(r.name().trim())
                .amount(r.amount())
                .currency(r.currency())
                .billingCycle(r.billingCycle())
                .trialDays(r.trialDays())
                .features(Set.copyOf(r.features()))
                .meteredRates(List.copyOf(r.meteredRates()))
                .active(true)
                .build()));
  }

  @Override
  @Transactional
  public PriceResponse archivePrice(UUID id) {
    MerchantPrice p = priceEntity(id, gate.lock());
    p.setActive(false);
    return mapper.price(p);
  }

  @Override
  public PageResponse<CustomerResponse> customers(Pageable page) {
    return PageResponse.from(customers.findAllByTenantId(gate.require(), page), mapper::customer);
  }

  @Override
  public CustomerResponse customer(UUID id) {
    return mapper.customer(customerEntity(id, gate.require()));
  }

  @Override
  public CustomerResponse customerByExternalId(String externalId) {
    UUID t = gate.require();
    return mapper.customer(
        customers
            .findByTenantIdAndExternalId(t, externalId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", externalId)));
  }

  @Override
  @Transactional
  public CustomerResponse createCustomer(CustomerRequest r) {
    UUID t = gate.lock();
    var existing = customers.findByTenantIdAndExternalId(t, r.externalId());
    if (existing.isPresent()) {
      var c = existing.get();
      if (!c.getName().equals(r.name().trim()) || !c.getEmail().equals(r.email().trim()))
        throw rule(
            "EXTERNAL_ID_CONFLICT", "External ID is already assigned to another customer payload");
      return mapper.customer(c);
    }
    return mapper.customer(
        customers.save(
            MerchantCustomer.builder()
                .tenantId(t)
                .externalId(r.externalId())
                .name(r.name().trim())
                .email(r.email().trim())
                .active(true)
                .build()));
  }

  @Override
  @Transactional
  public CustomerResponse updateCustomer(UUID id, CustomerUpdate r) {
    var c = customerEntity(id, gate.lock());
    c.setName(r.name().trim());
    c.setEmail(r.email().trim());
    c.setActive(r.active());
    return mapper.customer(c);
  }

  @Override
  public PageResponse<SubscriptionResponse> subscriptions(UUID customerId, Pageable page) {
    UUID t = gate.require();
    if (customerId != null) customerEntity(customerId, t);
    return PageResponse.from(
        customerId == null
            ? subscriptions.findAllByTenantId(t, page)
            : subscriptions.findAllByTenantIdAndCustomerId(t, customerId, page),
        this::subscriptionDto);
  }

  @Override
  public SubscriptionResponse subscription(UUID id) {
    return subscriptionDto(subscriptionEntity(id, gate.require()));
  }

  @Override
  @Transactional
  public SubscriptionResponse subscribe(SubscriptionRequest r) {
    UUID t = gate.lock();
    var previous = subscriptions.findByTenantIdAndIdempotencyKey(t, r.idempotencyKey());
    if (previous.isPresent()) {
      var s = previous.get();
      if (!s.getCustomerId().equals(r.customerId())
          || !s.getPriceId().equals(r.priceId())
          || s.getQuantity() != r.quantity()
          || s.isSkipTrial() != r.skipTrial())
        throw rule(
            "IDEMPOTENCY_CONFLICT", "This key was used with a different subscription payload");
      return subscriptionDto(s);
    }
    var c = customerEntity(r.customerId(), t);
    var p = priceEntity(r.priceId(), t);
    var product = productEntity(p.getProductId(), t);
    if (!c.isActive() || !p.isActive() || !product.isActive())
      throw rule("INACTIVE_CATALOG", "Customer, price and product must be active");
    if (subscriptions
        .findFirstByTenantIdAndCustomerIdAndProductIdAndStatusIn(
            t, c.getId(), product.getId(), LIVE)
        .isPresent())
      throw rule(
          "CUSTOMER_ALREADY_SUBSCRIBED",
          "Customer already has a live subscription for this product");
    Instant now = clock.instant();
    boolean trial = !r.skipTrial() && p.getTrialDays() > 0;
    var s =
        subscriptions.save(
            CustomerSubscription.builder()
                .tenantId(t)
                .customerId(c.getId())
                .productId(product.getId())
                .priceId(p.getId())
                .quantity(r.quantity())
                .status(trial ? "TRIALING" : "ACTIVE")
                .periodStart(now)
                .periodEnd(
                    trial
                        ? now.plus(Duration.ofDays(p.getTrialDays()))
                        : p.getBillingCycle().advance(now))
                .cancelAtPeriodEnd(false)
                .skipTrial(r.skipTrial())
                .idempotencyKey(r.idempotencyKey())
                .build());
    if (!trial)
      issue(
          s,
          p,
          "BASE",
          s.getPeriodStart(),
          s.getPeriodEnd(),
          p.getAmount().multiply(BigDecimal.valueOf(s.getQuantity())),
          BigDecimal.ZERO);
    return subscriptionDto(s);
  }

  @Override
  @Transactional
  public SubscriptionResponse cancel(UUID id, CancelRequest r) {
    var s = subscriptionEntity(id, gate.lock());
    if (s.getStatus().equals("CANCELED")) return subscriptionDto(s);
    if (r.atPeriodEnd() && s.getPeriodEnd().isAfter(clock.instant())) s.setCancelAtPeriodEnd(true);
    else {
      closeUsage(s, priceEntity(s.getPriceId(), s.getTenantId()));
      s.setStatus("CANCELED");
      s.setCancelAtPeriodEnd(false);
    }
    return subscriptionDto(s);
  }

  @Override
  public AccessResponse access(String externalId, UUID productId, String feature) {
    UUID t = gate.require();
    productEntity(productId, t);
    var c = customers.findByTenantIdAndExternalId(t, externalId);
    if (c.isEmpty() || !c.get().isActive()) return denied("CUSTOMER_UNAVAILABLE");
    var candidate =
        subscriptions.findFirstByTenantIdAndCustomerIdAndProductIdAndStatusIn(
            t, c.get().getId(), productId, LIVE);
    if (candidate.isEmpty()) return denied("NO_SUBSCRIPTION");
    var s = candidate.get();
    if (!entitled(s))
      return new AccessResponse(
          false, "SUBSCRIPTION_INACTIVE", s.getId(), Set.of(), s.getPeriodEnd());
    var p = priceEntity(s.getPriceId(), t);
    boolean allowed = feature == null || feature.isBlank() || p.getFeatures().contains(feature);
    return new AccessResponse(
        allowed,
        allowed ? "ACCESS_GRANTED" : "FEATURE_NOT_INCLUDED",
        s.getId(),
        p.getFeatures(),
        s.getPeriodEnd());
  }

  @Override
  @Transactional
  public UsageAccepted recordUsage(UsageRequest r) {
    UUID t = gate.lock();
    var prior = events.findByTenantIdAndIdempotencyKey(t, r.idempotencyKey());
    if (prior.isPresent()) {
      var e = prior.get();
      if (!e.getSubscriptionId().equals(r.subscriptionId())
          || !e.getMetric().equals(r.metric())
          || e.getQuantity().compareTo(r.quantity()) != 0)
        throw rule("IDEMPOTENCY_CONFLICT", "This key was used for another usage payload");
      return mapper.event(e);
    }
    var s = subscriptionEntity(r.subscriptionId(), t);
    if (!entitled(s))
      throw rule(
          "SUBSCRIPTION_INACTIVE", "Usage requires an active subscription in its current period");
    if (priceEntity(s.getPriceId(), t).getMeteredRates().stream()
        .noneMatch(rate -> rate.metric().equals(r.metric())))
      throw rule("METRIC_NOT_PRICED", "Metric is not configured on this price");
    return mapper.event(
        events.save(
            CustomerUsageEvent.builder()
                .tenantId(t)
                .subscriptionId(s.getId())
                .metric(r.metric())
                .quantity(r.quantity())
                .occurredAt(clock.instant())
                .idempotencyKey(r.idempotencyKey())
                .build()));
  }

  @Override
  public UsageResponse usage(UUID id) {
    return usageFor(subscriptionEntity(id, gate.require()));
  }

  private UsageResponse usageFor(CustomerSubscription s) {
    var p = priceEntity(s.getPriceId(), s.getTenantId());
    List<UsageMetric> metrics = new ArrayList<>();
    BigDecimal cost = BigDecimal.ZERO;
    for (var rate : p.getMeteredRates()) {
      BigDecimal total =
          events.total(
              s.getTenantId(), s.getId(), rate.metric(), s.getPeriodStart(), s.getPeriodEnd());
      BigDecimal overage = total.subtract(rate.includedQuantity()).max(BigDecimal.ZERO);
      BigDecimal amount =
          s.getStatus().equals("TRIALING")
              ? BigDecimal.ZERO.setScale(2)
              : money(overage.multiply(rate.unitPrice()));
      metrics.add(
          new UsageMetric(
              rate.metric(), total, rate.includedQuantity(), overage, rate.unitPrice(), amount));
      cost = cost.add(amount);
    }
    return new UsageResponse(
        s.getId(), p.getCurrency(), s.getPeriodStart(), s.getPeriodEnd(), metrics, money(cost));
  }

  @Override
  public PageResponse<InvoiceResponse> invoices(UUID customerId, Pageable page) {
    UUID t = gate.require();
    if (customerId != null) customerEntity(customerId, t);
    return PageResponse.from(
        customerId == null
            ? invoices.findAllByTenantId(t, page)
            : invoices.findByCustomer(t, customerId, page),
        this::invoiceDto);
  }

  @Override
  public InvoiceResponse invoice(UUID id) {
    return invoiceDto(invoiceEntity(id, gate.require()));
  }

  @Override
  @Transactional
  public InvoiceResponse markPaid(UUID id) {
    UUID t = gate.lock();
    var i = invoiceEntity(id, t);
    if (i.getStatus().equals("PAID")) return invoiceDto(i);
    if (i.getStatus().equals("VOID"))
      throw rule("INVOICE_NOT_PAYABLE", "Void invoices cannot be settled");
    i.setStatus("PAID");
    i.setAmountPaid(i.getTotal());
    i.setPaidAt(clock.instant());
    var s = subscriptionEntity(i.getSubscriptionId(), t);
    if (s.getStatus().equals("PAST_DUE")
        && !invoices.existsByTenantIdAndSubscriptionIdAndStatusAndDueAtBefore(
            t, s.getId(), "OPEN", clock.instant())) s.setStatus("ACTIVE");
    return invoiceDto(i);
  }

  @Override
  @Transactional
  public void advance(UUID tenant, UUID id) {
    TenantContextHolder.runAs(
        tenant,
        () -> {
          if (!gate.unlocked(tenant)) return null;
          gate.lock();
          var s = subscriptionEntity(id, tenant);
          Instant now = clock.instant();
          if (!List.of("ACTIVE", "TRIALING").contains(s.getStatus())
              || s.getPeriodEnd().isAfter(now)) return null;
          var p = priceEntity(s.getPriceId(), tenant);
          closeUsage(s, p);
          if (s.isCancelAtPeriodEnd()) {
            s.setStatus("CANCELED");
            return null;
          }
          if (invoices.existsByTenantIdAndSubscriptionIdAndStatusAndDueAtBefore(
              tenant, id, "OPEN", now)) {
            s.setStatus("PAST_DUE");
            return null;
          }
          // Advance exactly one period per pass; repeated passes catch up after downtime without
          // skipping charges.
          Instant start = s.getPeriodEnd();
          s.setPeriodStart(start);
          s.setPeriodEnd(p.getBillingCycle().advance(start));
          s.setStatus("ACTIVE");
          issue(
              s,
              p,
              "BASE",
              start,
              s.getPeriodEnd(),
              p.getAmount().multiply(BigDecimal.valueOf(s.getQuantity())),
              BigDecimal.ZERO);
          return null;
        });
  }

  private boolean entitled(CustomerSubscription s) {
    return List.of("ACTIVE", "TRIALING").contains(s.getStatus())
        && s.getPeriodEnd().isAfter(clock.instant())
        && customerEntity(s.getCustomerId(), s.getTenantId()).isActive()
        && !invoices.existsByTenantIdAndSubscriptionIdAndStatusAndDueAtBefore(
            s.getTenantId(), s.getId(), "OPEN", clock.instant());
  }

  private void closeUsage(CustomerSubscription s, MerchantPrice p) {
    if (s.getStatus().equals("TRIALING")) return; // Trial consumption is free.
    BigDecimal cost = usageFor(s).estimatedOverageTotal();
    if (cost.signum() > 0)
      issue(s, p, "USAGE", s.getPeriodStart(), s.getPeriodEnd(), BigDecimal.ZERO, cost);
  }

  private void issue(
      CustomerSubscription s,
      MerchantPrice p,
      String kind,
      Instant start,
      Instant end,
      BigDecimal base,
      BigDecimal usage) {
    if (invoices
        .findByTenantIdAndSubscriptionIdAndPeriodStartAndKind(
            s.getTenantId(), s.getId(), start, kind)
        .isPresent()) return;
    BigDecimal total = money(base.add(usage));
    invoices.save(
        CustomerInvoice.builder()
            .tenantId(s.getTenantId())
            .subscriptionId(s.getId())
            .invoiceNumber("CUS-" + UUID.randomUUID())
            .currency(p.getCurrency())
            .kind(kind)
            .baseAmount(money(base))
            .usageAmount(money(usage))
            .total(total)
            .amountPaid(total.signum() == 0 ? total : BigDecimal.ZERO)
            .status(total.signum() == 0 ? "PAID" : "OPEN")
            .paidAt(total.signum() == 0 ? clock.instant() : null)
            .periodStart(start)
            .periodEnd(end)
            .dueAt(clock.instant().plus(Duration.ofDays(14)))
            .build());
  }

  private SubscriptionResponse subscriptionDto(CustomerSubscription s) {
    UUID t = s.getTenantId();
    return mapper.subscription(
        s,
        customerEntity(s.getCustomerId(), t),
        productEntity(s.getProductId(), t),
        priceEntity(s.getPriceId(), t));
  }

  private InvoiceResponse invoiceDto(CustomerInvoice i) {
    var s = subscriptionEntity(i.getSubscriptionId(), i.getTenantId());
    return mapper.invoice(i, customerEntity(s.getCustomerId(), i.getTenantId()));
  }

  private MerchantProduct productEntity(UUID id, UUID t) {
    return products
        .findByIdAndTenantId(id, t)
        .orElseThrow(() -> new ResourceNotFoundException("Product", id));
  }

  private MerchantPrice priceEntity(UUID id, UUID t) {
    return prices
        .findByIdAndTenantId(id, t)
        .orElseThrow(() -> new ResourceNotFoundException("Price", id));
  }

  private MerchantCustomer customerEntity(UUID id, UUID t) {
    return customers
        .findByIdAndTenantId(id, t)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
  }

  private CustomerSubscription subscriptionEntity(UUID id, UUID t) {
    return subscriptions
        .findByIdAndTenantId(id, t)
        .orElseThrow(() -> new ResourceNotFoundException("Customer subscription", id));
  }

  private CustomerInvoice invoiceEntity(UUID id, UUID t) {
    return invoices
        .findByIdAndTenantId(id, t)
        .orElseThrow(() -> new ResourceNotFoundException("Customer invoice", id));
  }

  private static AccessResponse denied(String reason) {
    return new AccessResponse(false, reason, null, Set.of(), null);
  }

  private static BusinessRuleException rule(String code, String message) {
    return new BusinessRuleException(code, message);
  }

  private static BigDecimal money(BigDecimal amount) {
    return amount.setScale(2, RoundingMode.HALF_UP);
  }
}
