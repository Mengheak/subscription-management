package com.chheang.mengheak.subscriptionmanager.service.billing;

import com.chheang.mengheak.subscriptionmanager.common.enums.InvoiceLineType;
import com.chheang.mengheak.subscriptionmanager.common.enums.InvoiceStatus;
import com.chheang.mengheak.subscriptionmanager.property.BillingProperties;
import com.chheang.mengheak.subscriptionmanager.entity.Invoice;
import com.chheang.mengheak.subscriptionmanager.entity.InvoiceLineItem;
import com.chheang.mengheak.subscriptionmanager.entity.Plan;
import com.chheang.mengheak.subscriptionmanager.entity.PlanMeteredRate;
import com.chheang.mengheak.subscriptionmanager.entity.Subscription;
import com.chheang.mengheak.subscriptionmanager.entity.UsageCounter;
import com.chheang.mengheak.subscriptionmanager.repository.InvoiceRepository;
import com.chheang.mengheak.subscriptionmanager.service.billing.ProrationCalculator.ProrationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceAssembler {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private final BillingProperties billingProperties;
    private final InvoiceRepository invoiceRepository;
    private final Clock clock;

    public Invoice openInvoice(Subscription subscription, Instant periodStart, Instant periodEnd) {
        return Invoice.builder()
                .tenantId(subscription.getTenantId())
                .subscription(subscription)
                .invoiceNumber(nextInvoiceNumber())
                .status(InvoiceStatus.DRAFT)
                .currency(subscription.getPlan().getCurrency())
                .subtotal(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .amountPaid(BigDecimal.ZERO)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .build();
    }

    public void addSubscriptionLine(Invoice invoice, Subscription subscription) {
        Plan plan = subscription.getPlan();
        BigDecimal quantity = BigDecimal.valueOf(subscription.getQuantity());
        BigDecimal amount = billingProperties.toMoney(plan.getBasePrice().multiply(quantity));

        invoice.addLineItem(InvoiceLineItem.builder()
                .lineType(InvoiceLineType.SUBSCRIPTION)
                .description("%s (%s to %s)".formatted(
                        plan.getName(),
                        PERIOD_FORMAT.format(invoice.getPeriodStart()),
                        PERIOD_FORMAT.format(invoice.getPeriodEnd())))
                .quantity(billingProperties.toQuantity(quantity))
                .unitPrice(plan.getBasePrice())
                .amount(amount)
                .periodStart(invoice.getPeriodStart())
                .periodEnd(invoice.getPeriodEnd())
                .build());
    }

    public void addUsageLines(Invoice invoice, Subscription subscription, Collection<UsageCounter> counters) {
        Plan plan = subscription.getPlan();

        for (UsageCounter counter : counters) {
            Optional<PlanMeteredRate> rate = plan.rateFor(counter.getMetric());
            if (rate.isEmpty()) {
                log.debug("Plan {} has no rate for metric {}; skipping usage line",
                        plan.getCode(), counter.getMetric());
                continue;
            }

            PlanMeteredRate meteredRate = rate.get();
            BigDecimal overage = counter.getTotalQuantity().subtract(meteredRate.getIncludedQuantity());
            if (overage.signum() <= 0) {
                continue;
            }

            BigDecimal amount = billingProperties.toMoney(overage.multiply(meteredRate.getUnitPrice()));

            invoice.addLineItem(InvoiceLineItem.builder()
                    .lineType(InvoiceLineType.USAGE)
                    .description("%s overage (%s of %s included)".formatted(
                            counter.getMetric(),
                            counter.getTotalQuantity().stripTrailingZeros().toPlainString(),
                            meteredRate.getIncludedQuantity().stripTrailingZeros().toPlainString()))
                    .metric(counter.getMetric())
                    .quantity(billingProperties.toQuantity(overage))
                    .unitPrice(meteredRate.getUnitPrice())
                    .amount(amount)
                    .periodStart(counter.getPeriodStart())
                    .periodEnd(counter.getPeriodEnd())
                    .build());
        }
    }

    public void addProrationLines(Invoice invoice,
                                  Subscription subscription,
                                  Plan newPlan,
                                  int newQuantity,
                                  ProrationResult proration) {

        Plan oldPlan = subscription.getPlan();
        String window = "%s to %s".formatted(
                PERIOD_FORMAT.format(proration.effectiveAt()),
                PERIOD_FORMAT.format(proration.periodEnd()));

        if (proration.creditAmount().signum() > 0) {
            invoice.addLineItem(InvoiceLineItem.builder()
                    .lineType(InvoiceLineType.PRORATION_CREDIT)
                    .description("Unused %s (%s)".formatted(oldPlan.getName(), window))
                    .quantity(billingProperties.toQuantity(BigDecimal.valueOf(subscription.getQuantity())))
                    .unitPrice(oldPlan.getBasePrice())
                    .amount(proration.creditAmount().negate())
                    .periodStart(proration.effectiveAt())
                    .periodEnd(proration.periodEnd())
                    .build());
        }

        if (proration.chargeAmount().signum() > 0) {
            invoice.addLineItem(InvoiceLineItem.builder()
                    .lineType(InvoiceLineType.PRORATION_CHARGE)
                    .description("%s (%s)".formatted(newPlan.getName(), window))
                    .quantity(billingProperties.toQuantity(BigDecimal.valueOf(newQuantity)))
                    .unitPrice(newPlan.getBasePrice())
                    .amount(proration.chargeAmount())
                    .periodStart(proration.effectiveAt())
                    .periodEnd(proration.periodEnd())
                    .build());
        }
    }

    public Invoice finalise(Invoice invoice) {
        BigDecimal subtotal = invoice.getLineItems().stream()
                .map(InvoiceLineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        subtotal = billingProperties.toMoney(subtotal);

        BigDecimal tax = subtotal.signum() > 0
                ? billingProperties.toMoney(subtotal.multiply(billingProperties.taxRate()))
                : billingProperties.toMoney(BigDecimal.ZERO);

        Instant now = clock.instant();
        invoice.setSubtotal(subtotal);
        invoice.setTaxAmount(tax);
        invoice.setTotal(billingProperties.toMoney(subtotal.add(tax)));
        invoice.setStatus(InvoiceStatus.OPEN);
        invoice.setIssuedAt(now);
        invoice.setDueAt(now.plus(java.time.Duration.ofDays(billingProperties.invoiceDueDays())));
        return invoice;
    }

    public String nextInvoiceNumber() {
        long sequence = invoiceRepository.nextInvoiceSequenceValue();
        return "%s-%06d".formatted(billingProperties.invoiceNumberPrefix(), sequence);
    }
}
