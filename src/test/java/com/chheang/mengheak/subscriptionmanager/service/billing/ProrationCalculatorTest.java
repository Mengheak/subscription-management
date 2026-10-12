package com.chheang.mengheak.subscriptionmanager.service.billing;

import com.chheang.mengheak.subscriptionmanager.common.enums.BillingCycle;
import com.chheang.mengheak.subscriptionmanager.config.properties.BillingProperties;
import com.chheang.mengheak.subscriptionmanager.entity.Plan;
import com.chheang.mengheak.subscriptionmanager.entity.Subscription;
import com.chheang.mengheak.subscriptionmanager.service.billing.ProrationCalculator.ProrationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProrationCalculatorTest {

    private static final Instant PERIOD_START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant PERIOD_END = Instant.parse("2026-02-01T00:00:00Z");

    private ProrationCalculator calculator;

    @BeforeEach
    void setUp() {
        BillingProperties billingProperties = new BillingProperties(
                "USD", 2, 4, RoundingMode.HALF_UP, true, 14, "INV", BigDecimal.ZERO);
        calculator = new ProrationCalculator(billingProperties);
    }

    @Nested
    @DisplayName("upgrade")
    class Upgrade {

        @Test
        @DisplayName("at the exact midpoint credits and charges half of each plan")
        void halfwayThroughPeriod() {
            Subscription subscription = subscriptionOn(plan("PRO", "100.0000"), 1);

            ProrationResult result = calculator.calculate(
                    subscription, plan("ENTERPRISE", "200.0000"), 1,
                    Instant.parse("2026-01-16T12:00:00Z"));

            assertThat(result.remainingRatio()).isEqualByComparingTo("0.5");
            assertThat(result.creditAmount()).isEqualByComparingTo("50.00");
            assertThat(result.chargeAmount()).isEqualByComparingTo("100.00");
            assertThat(result.netAmount()).isEqualByComparingTo("50.00");
            assertThat(result.isCharge()).isTrue();
        }

        @Test
        @DisplayName("rounds each amount to the money scale, not the ratio")
        void roundsOnlyFinalAmounts() {
            Subscription subscription = subscriptionOn(plan("PRO", "100.0000"), 1);

            ProrationResult result = calculator.calculate(
                    subscription, plan("ENTERPRISE", "200.0000"), 1,
                    Instant.parse("2026-01-11T00:00:00Z"));

            assertThat(result.creditAmount()).isEqualByComparingTo("67.74");
            assertThat(result.chargeAmount()).isEqualByComparingTo("135.48");
            assertThat(result.netAmount()).isEqualByComparingTo("67.74");
        }

        @Test
        @DisplayName("multiplies both sides by their own quantity")
        void appliesQuantity() {
            Subscription subscription = subscriptionOn(plan("PRO", "100.0000"), 3);

            ProrationResult result = calculator.calculate(
                    subscription, plan("ENTERPRISE", "200.0000"), 5,
                    Instant.parse("2026-01-16T12:00:00Z"));

            assertThat(result.creditAmount()).isEqualByComparingTo("150.00");
            assertThat(result.chargeAmount()).isEqualByComparingTo("500.00");
            assertThat(result.netAmount()).isEqualByComparingTo("350.00");
        }
    }

    @Nested
    @DisplayName("downgrade")
    class Downgrade {

        @Test
        @DisplayName("produces a negative net, i.e. a credit balance")
        void netIsNegative() {
            Subscription subscription = subscriptionOn(plan("ENTERPRISE", "200.0000"), 1);

            ProrationResult result = calculator.calculate(
                    subscription, plan("PRO", "100.0000"), 1,
                    Instant.parse("2026-01-16T12:00:00Z"));

            assertThat(result.creditAmount()).isEqualByComparingTo("100.00");
            assertThat(result.chargeAmount()).isEqualByComparingTo("50.00");
            assertThat(result.netAmount()).isEqualByComparingTo("-50.00");
            assertThat(result.isCharge()).isFalse();
        }
    }

    @Nested
    @DisplayName("boundaries")
    class Boundaries {

        @Test
        @DisplayName("a change at the period end prorates nothing")
        void atPeriodEnd() {
            Subscription subscription = subscriptionOn(plan("PRO", "100.0000"), 1);

            ProrationResult result = calculator.calculate(
                    subscription, plan("ENTERPRISE", "200.0000"), 1, PERIOD_END);

            assertThat(result.remainingRatio()).isEqualByComparingTo("0");
            assertThat(result.isZero()).isTrue();
        }

        @Test
        @DisplayName("a change past the period end never refunds more than the period")
        void clampsAfterPeriodEnd() {
            Subscription subscription = subscriptionOn(plan("PRO", "100.0000"), 1);

            ProrationResult result = calculator.calculate(
                    subscription, plan("ENTERPRISE", "200.0000"), 1,
                    Instant.parse("2026-03-01T00:00:00Z"));

            assertThat(result.isZero()).isTrue();
        }

        @Test
        @DisplayName("a change at the period start prorates the whole period")
        void atPeriodStart() {
            Subscription subscription = subscriptionOn(plan("PRO", "100.0000"), 1);

            ProrationResult result = calculator.calculate(
                    subscription, plan("ENTERPRISE", "200.0000"), 1, PERIOD_START);

            assertThat(result.remainingRatio()).isEqualByComparingTo("1");
            assertThat(result.creditAmount()).isEqualByComparingTo("100.00");
            assertThat(result.chargeAmount()).isEqualByComparingTo("200.00");
        }

        @Test
        @DisplayName("clock skew before the period start cannot produce a ratio above 1")
        void clampsBeforePeriodStart() {
            Subscription subscription = subscriptionOn(plan("PRO", "100.0000"), 1);

            ProrationResult result = calculator.calculate(
                    subscription, plan("ENTERPRISE", "200.0000"), 1,
                    Instant.parse("2025-12-25T00:00:00Z"));

            assertThat(result.remainingRatio()).isEqualByComparingTo("1");
            assertThat(result.creditAmount()).isEqualByComparingTo("100.00");
        }
    }

    private static Plan plan(String code, String basePrice) {
        return Plan.builder()
                .code(code)
                .name(code)
                .billingCycle(BillingCycle.MONTHLY)
                .basePrice(new BigDecimal(basePrice))
                .currency("USD")
                .trialDays(0)
                .active(true)
                .build();
    }

    private static Subscription subscriptionOn(Plan plan, int quantity) {
        return Subscription.builder()
                .plan(plan)
                .quantity(quantity)
                .startedAt(PERIOD_START)
                .currentPeriodStart(PERIOD_START)
                .currentPeriodEnd(PERIOD_END)
                .build();
    }
}
