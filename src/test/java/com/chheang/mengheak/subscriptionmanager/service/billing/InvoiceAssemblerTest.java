package com.chheang.mengheak.subscriptionmanager.service.billing;

import com.chheang.mengheak.subscriptionmanager.common.enums.BillingCycle;
import com.chheang.mengheak.subscriptionmanager.common.enums.InvoiceLineType;
import com.chheang.mengheak.subscriptionmanager.common.enums.InvoiceStatus;
import com.chheang.mengheak.subscriptionmanager.common.enums.UsageMetric;
import com.chheang.mengheak.subscriptionmanager.property.BillingProperties;
import com.chheang.mengheak.subscriptionmanager.entity.Invoice;
import com.chheang.mengheak.subscriptionmanager.entity.InvoiceLineItem;
import com.chheang.mengheak.subscriptionmanager.entity.Plan;
import com.chheang.mengheak.subscriptionmanager.entity.PlanMeteredRate;
import com.chheang.mengheak.subscriptionmanager.entity.Subscription;
import com.chheang.mengheak.subscriptionmanager.entity.UsageCounter;
import com.chheang.mengheak.subscriptionmanager.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InvoiceAssemblerTest {

    private static final Instant NOW = Instant.parse("2026-09-06T12:00:00Z");
    private static final Instant PERIOD_START = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant PERIOD_END = Instant.parse("2026-10-01T00:00:00Z");
    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock
    private InvoiceRepository invoiceRepository;

    private InvoiceAssembler assembler;
    private BillingProperties billingProperties;

    @BeforeEach
    void setUp() {
        billingProperties = new BillingProperties(
                "USD", 2, 4, RoundingMode.HALF_UP, true, 14, "INV", new BigDecimal("0.20"));
        assembler = new InvoiceAssembler(billingProperties, invoiceRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("numbers invoices from the database sequence")
    void formatsInvoiceNumber() {
        given(invoiceRepository.nextInvoiceSequenceValue()).willReturn(42L);

        assertThat(assembler.nextInvoiceNumber()).isEqualTo("INV-000042");
    }

    @Test
    @DisplayName("opens a draft with zeroed totals so it can never be mistaken for payable")
    void opensDraft() {
        given(invoiceRepository.nextInvoiceSequenceValue()).willReturn(1L);
        Subscription subscription = subscription(1);

        Invoice invoice = assembler.openInvoice(subscription, PERIOD_START, PERIOD_END);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(invoice.getTotal()).isEqualByComparingTo("0");
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("0");
        assertThat(invoice.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(invoice.getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("bills base fee x quantity plus metered overage, and totals to the sum of the lines")
    void billsSubscriptionAndOverage() {
        given(invoiceRepository.nextInvoiceSequenceValue()).willReturn(7L);
        Subscription subscription = subscription(2);

        Invoice invoice = assembler.openInvoice(subscription, PERIOD_START, PERIOD_END);
        assembler.addSubscriptionLine(invoice, subscription);
        assembler.addUsageLines(invoice, subscription,
                List.of(counter(UsageMetric.API_CALLS, "612500.0000")));
        assembler.finalise(invoice);

        assertThat(invoice.getLineItems()).hasSize(2);
        assertThat(lineAmount(invoice, InvoiceLineType.SUBSCRIPTION)).isEqualByComparingTo("198.00");
        assertThat(lineAmount(invoice, InvoiceLineType.USAGE)).isEqualByComparingTo("16.88");

        assertThat(invoice.getSubtotal()).isEqualByComparingTo("214.88");
        assertThat(invoice.getTaxAmount()).isEqualByComparingTo("42.98");
        assertThat(invoice.getTotal()).isEqualByComparingTo("257.86");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(invoice.getIssuedAt()).isEqualTo(NOW);
        assertThat(invoice.getDueAt()).isEqualTo(NOW.plusSeconds(14 * 86400L));
    }

    @Test
    @DisplayName("consumption inside the allowance produces no line at all")
    void noLineWhenWithinAllowance() {
        given(invoiceRepository.nextInvoiceSequenceValue()).willReturn(8L);
        Subscription subscription = subscription(1);

        Invoice invoice = assembler.openInvoice(subscription, PERIOD_START, PERIOD_END);
        assembler.addUsageLines(invoice, subscription,
                List.of(counter(UsageMetric.API_CALLS, "12000.0000")));

        assertThat(invoice.getLineItems()).isEmpty();
    }

    @Test
    @DisplayName("writes proration as a negative credit and a positive charge, never a netted figure")
    void prorationKeepsBothLines() {
        given(invoiceRepository.nextInvoiceSequenceValue()).willReturn(9L);
        Subscription subscription = subscription(1);

        Invoice invoice = assembler.openInvoice(subscription, NOW, PERIOD_END);
        assembler.addProrationLines(invoice, subscription, enterprisePlan(), 1,
                new ProrationCalculator.ProrationResult(
                        new BigDecimal("45.20"), new BigDecimal("149.30"), new BigDecimal("104.10"),
                        new BigDecimal("0.5"), NOW, PERIOD_END));
        assembler.finalise(invoice);

        assertThat(lineAmount(invoice, InvoiceLineType.PRORATION_CREDIT)).isEqualByComparingTo("-45.20");
        assertThat(lineAmount(invoice, InvoiceLineType.PRORATION_CHARGE)).isEqualByComparingTo("149.30");
        assertThat(invoice.getSubtotal()).isEqualByComparingTo("104.10");
    }

    @Test
    @DisplayName("a net-credit invoice does not generate negative tax")
    void noNegativeTax() {
        given(invoiceRepository.nextInvoiceSequenceValue()).willReturn(10L);
        Subscription subscription = subscription(1);

        Invoice invoice = assembler.openInvoice(subscription, NOW, PERIOD_END);
        assembler.addProrationLines(invoice, subscription, cheapPlan(), 1,
                new ProrationCalculator.ProrationResult(
                        new BigDecimal("80.00"), new BigDecimal("20.00"), new BigDecimal("-60.00"),
                        new BigDecimal("0.5"), NOW, PERIOD_END));
        assembler.finalise(invoice);

        assertThat(invoice.getSubtotal()).isEqualByComparingTo("-60.00");
        assertThat(invoice.getTaxAmount()).isEqualByComparingTo("0.00");
        assertThat(invoice.getTotal()).isEqualByComparingTo("-60.00");
    }

    private static BigDecimal lineAmount(Invoice invoice, InvoiceLineType type) {
        return invoice.getLineItems().stream()
                .filter(line -> line.getLineType() == type)
                .map(InvoiceLineItem::getAmount)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + type + " line on the invoice"));
    }

    private static UsageCounter counter(UsageMetric metric, String total) {
        return UsageCounter.builder()
                .tenantId(TENANT_ID)
                .metric(metric)
                .periodStart(PERIOD_START)
                .periodEnd(PERIOD_END)
                .totalQuantity(new BigDecimal(total))
                .build();
    }

    private static Subscription subscription(int quantity) {
        return Subscription.builder()
                .tenantId(TENANT_ID)
                .plan(proPlan())
                .quantity(quantity)
                .startedAt(PERIOD_START)
                .currentPeriodStart(PERIOD_START)
                .currentPeriodEnd(PERIOD_END)
                .build();
    }

    private static Plan proPlan() {
        Plan plan = basePlan("PRO", "Professional", "99.0000");
        plan.addMeteredRate(PlanMeteredRate.builder()
                .metric(UsageMetric.API_CALLS)
                .includedQuantity(new BigDecimal("500000.0000"))
                .unitPrice(new BigDecimal("0.000150"))
                .build());
        return plan;
    }

    private static Plan enterprisePlan() {
        return basePlan("ENTERPRISE", "Enterprise", "299.0000");
    }

    private static Plan cheapPlan() {
        return basePlan("STARTER", "Starter", "29.0000");
    }

    private static Plan basePlan(String code, String name, String price) {
        return Plan.builder()
                .code(code)
                .name(name)
                .billingCycle(BillingCycle.MONTHLY)
                .basePrice(new BigDecimal(price))
                .currency("USD")
                .trialDays(0)
                .active(true)
                .build();
    }
}
