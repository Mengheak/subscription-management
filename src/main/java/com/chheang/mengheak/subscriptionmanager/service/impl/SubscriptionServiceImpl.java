package com.chheang.mengheak.subscriptionmanager.service.impl;

import com.chheang.mengheak.subscriptionmanager.common.enums.SubscriptionStatus;
import com.chheang.mengheak.subscriptionmanager.exception.BusinessRuleException;
import com.chheang.mengheak.subscriptionmanager.exception.ResourceNotFoundException;
import com.chheang.mengheak.subscriptionmanager.property.BillingProperties;
import com.chheang.mengheak.subscriptionmanager.common.PageResponse;
import com.chheang.mengheak.subscriptionmanager.dto.subscription.CancelSubscriptionRequest;
import com.chheang.mengheak.subscriptionmanager.dto.subscription.ChangePlanRequest;
import com.chheang.mengheak.subscriptionmanager.dto.subscription.CreateSubscriptionRequest;
import com.chheang.mengheak.subscriptionmanager.dto.subscription.SubscriptionResponse;
import com.chheang.mengheak.subscriptionmanager.entity.Invoice;
import com.chheang.mengheak.subscriptionmanager.entity.Plan;
import com.chheang.mengheak.subscriptionmanager.entity.Subscription;
import com.chheang.mengheak.subscriptionmanager.mapper.SubscriptionMapper;
import com.chheang.mengheak.subscriptionmanager.repository.InvoiceRepository;
import com.chheang.mengheak.subscriptionmanager.repository.PlanRepository;
import com.chheang.mengheak.subscriptionmanager.repository.SubscriptionRepository;
import com.chheang.mengheak.subscriptionmanager.security.TenantContextHolder;
import com.chheang.mengheak.subscriptionmanager.service.SubscriptionService;
import com.chheang.mengheak.subscriptionmanager.service.billing.InvoiceAssembler;
import com.chheang.mengheak.subscriptionmanager.service.billing.ProrationCalculator;
import com.chheang.mengheak.subscriptionmanager.service.billing.ProrationCalculator.ProrationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final InvoiceRepository invoiceRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final ProrationCalculator prorationCalculator;
    private final InvoiceAssembler invoiceAssembler;
    private final BillingProperties billingProperties;
    private final Clock clock;

    @Override
    @Transactional
    public SubscriptionResponse create(CreateSubscriptionRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();

        if (subscriptionRepository.existsByTenantIdAndStatusIn(tenantId, SubscriptionStatus.liveStatuses())) {
            throw new BusinessRuleException("SUBSCRIPTION_ALREADY_EXISTS",
                    "This tenant already has a live subscription; change or cancel it instead");
        }

        Plan plan = requireActivePlan(request.planCode());
        Instant now = clock.instant();

        boolean startsInTrial = !request.skipTrial() && plan.getTrialDays() > 0;
        Instant trialEnd = startsInTrial ? now.plus(Duration.ofDays(plan.getTrialDays())) : null;

        Instant periodEnd = startsInTrial ? trialEnd : plan.getBillingCycle().advance(now);

        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .tenantId(tenantId)
                .plan(plan)
                .status(startsInTrial ? SubscriptionStatus.TRIALING : SubscriptionStatus.ACTIVE)
                .quantity(request.quantity())
                .startedAt(now)
                .currentPeriodStart(now)
                .currentPeriodEnd(periodEnd)
                .trialEnd(trialEnd)
                .cancelAtPeriodEnd(false)
                .build());

        log.info("Tenant {} subscribed to plan {} (quantity={}, trialing={})",
                tenantId, plan.getCode(), request.quantity(), startsInTrial);

        return subscriptionMapper.toResponse(subscription);
    }

    @Override
    @Transactional
    public SubscriptionResponse changePlan(UUID subscriptionId, ChangePlanRequest request) {
        Subscription subscription = requireSubscription(subscriptionId);

        if (!subscription.isLive()) {
            throw new BusinessRuleException("SUBSCRIPTION_NOT_LIVE",
                    "Subscription is %s and can no longer change plan".formatted(subscription.getStatus()));
        }
        if (subscription.isCancelAtPeriodEnd()) {
            throw new BusinessRuleException("SUBSCRIPTION_PENDING_CANCELLATION",
                    "Subscription is scheduled for cancellation; resume it before changing plan");
        }

        Plan newPlan = requireActivePlan(request.planCode());
        Plan currentPlan = subscription.getPlan();
        int newQuantity = request.quantity() != null ? request.quantity() : subscription.getQuantity();

        if (newPlan.getId().equals(currentPlan.getId()) && newQuantity == subscription.getQuantity()) {
            throw new BusinessRuleException("NO_CHANGE_REQUESTED",
                    "The subscription is already on plan %s with quantity %d"
                            .formatted(newPlan.getCode(), newQuantity));
        }
        if (!newPlan.getCurrency().equals(currentPlan.getCurrency())) {
            throw new BusinessRuleException("CURRENCY_MISMATCH",
                    "Cannot move from a %s plan to a %s plan mid-subscription"
                            .formatted(currentPlan.getCurrency(), newPlan.getCurrency()));
        }

        Instant now = clock.instant();
        boolean prorate = request.prorate() != null ? request.prorate() : billingProperties.prorationEnabled();
        boolean inTrial = subscription.getStatus() == SubscriptionStatus.TRIALING
                && subscription.isTrialing(now);

        if (prorate && !inTrial) {
            settleProration(subscription, newPlan, newQuantity, now);
        } else {
            log.debug("Skipping proration for subscription {} (prorate={}, inTrial={})",
                    subscriptionId, prorate, inTrial);
        }

        int previousQuantity = subscription.getQuantity();
        subscription.setPlan(newPlan);
        subscription.setQuantity(newQuantity);

        log.info("Subscription {} moved from {} to {} (quantity {} -> {})",
                subscriptionId, currentPlan.getCode(), newPlan.getCode(), previousQuantity, newQuantity);

        return subscriptionMapper.toResponse(subscription);
    }

    private void settleProration(Subscription subscription, Plan newPlan, int newQuantity, Instant effectiveAt) {
        ProrationResult proration = prorationCalculator.calculate(subscription, newPlan, newQuantity, effectiveAt);

        if (proration.isZero()) {
            log.debug("Proration for subscription {} is zero; no settlement invoice issued", subscription.getId());
            return;
        }

        Invoice invoice = invoiceAssembler.openInvoice(subscription, effectiveAt, subscription.getCurrentPeriodEnd());
        invoiceAssembler.addProrationLines(invoice, subscription, newPlan, newQuantity, proration);
        invoiceAssembler.finalise(invoice);
        invoiceRepository.save(invoice);

        log.info("Issued proration invoice {} for subscription {}: credit={} charge={} net={}",
                invoice.getInvoiceNumber(), subscription.getId(),
                proration.creditAmount(), proration.chargeAmount(), proration.netAmount());
    }

    @Override
    @Transactional
    public SubscriptionResponse cancel(UUID subscriptionId, CancelSubscriptionRequest request) {
        Subscription subscription = requireSubscription(subscriptionId);

        if (!subscription.isLive()) {
            throw new BusinessRuleException("SUBSCRIPTION_NOT_LIVE",
                    "Subscription is already %s".formatted(subscription.getStatus()));
        }

        Instant now = clock.instant();

        if (request.atPeriodEnd()) {
            subscription.setCancelAtPeriodEnd(true);
            log.info("Subscription {} scheduled for cancellation at {} (reason: {})",
                    subscriptionId, subscription.getCurrentPeriodEnd(), request.reason());
        } else {
            subscription.setStatus(SubscriptionStatus.CANCELED);
            subscription.setCancelAtPeriodEnd(false);
            subscription.setCanceledAt(now);
            subscription.setEndedAt(now);
            log.info("Subscription {} canceled immediately (reason: {})", subscriptionId, request.reason());
        }

        return subscriptionMapper.toResponse(subscription);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse get(UUID subscriptionId) {
        return subscriptionMapper.toResponse(requireSubscription(subscriptionId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SubscriptionResponse> list(Pageable pageable) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        return PageResponse.from(
                subscriptionRepository.findAllByTenantId(tenantId, pageable),
                subscriptionMapper::toResponse);
    }

    private Subscription requireSubscription(UUID subscriptionId) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        return subscriptionRepository.findByIdAndTenantId(subscriptionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", subscriptionId));
    }

    private Plan requireActivePlan(String planCode) {
        return planRepository.findWithRatesByCode(planCode)
                .filter(Plan::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Active plan", planCode));
    }
}
