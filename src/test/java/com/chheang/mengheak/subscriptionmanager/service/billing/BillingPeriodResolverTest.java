package com.chheang.mengheak.subscriptionmanager.service.billing;

import com.chheang.mengheak.subscriptionmanager.common.enums.BillingCycle;
import com.chheang.mengheak.subscriptionmanager.entity.Plan;
import com.chheang.mengheak.subscriptionmanager.entity.Subscription;
import com.chheang.mengheak.subscriptionmanager.service.billing.BillingPeriodResolver.BillingPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BillingPeriodResolverTest {

    private final BillingPeriodResolver resolver = new BillingPeriodResolver();

    @Test
    @DisplayName("an event inside the current period resolves to it")
    void withinCurrentPeriod() {
        Subscription subscription = monthlySubscription(
                Instant.parse("2026-02-01T00:00:00Z"), Instant.parse("2026-03-01T00:00:00Z"));

        BillingPeriod period = resolver.resolve(subscription, Instant.parse("2026-02-15T10:00:00Z"));

        assertThat(period.start()).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
        assertThat(period.end()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
    }

    @Test
    @DisplayName("an event from last month resolves to the previous period")
    void walksBackwardsForLateEvents() {
        Subscription subscription = monthlySubscription(
                Instant.parse("2026-02-01T00:00:00Z"), Instant.parse("2026-03-01T00:00:00Z"));

        BillingPeriod period = resolver.resolve(subscription, Instant.parse("2026-01-15T10:00:00Z"));

        assertThat(period.start()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(period.end()).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
    }

    @Test
    @DisplayName("an event after the current period resolves to the next one")
    void walksForwards() {
        Subscription subscription = monthlySubscription(
                Instant.parse("2026-02-01T00:00:00Z"), Instant.parse("2026-03-01T00:00:00Z"));

        BillingPeriod period = resolver.resolve(subscription, Instant.parse("2026-03-15T10:00:00Z"));

        assertThat(period.start()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
        assertThat(period.end()).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));
    }

    @Test
    @DisplayName("periods are half-open, so a boundary instant belongs to exactly one period")
    void boundaryBelongsToTheFollowingPeriod() {
        Subscription subscription = monthlySubscription(
                Instant.parse("2026-02-01T00:00:00Z"), Instant.parse("2026-03-01T00:00:00Z"));

        BillingPeriod period = resolver.resolve(subscription, Instant.parse("2026-03-01T00:00:00Z"));

        assertThat(period.start()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
        assertThat(period.contains(Instant.parse("2026-03-01T00:00:00Z"))).isTrue();
    }

    @Test
    @DisplayName("an absurd timestamp falls back to the current period instead of spinning")
    void boundsTheWalk() {
        Subscription subscription = monthlySubscription(
                Instant.parse("2026-02-01T00:00:00Z"), Instant.parse("2026-03-01T00:00:00Z"));

        BillingPeriod period = resolver.resolve(subscription, Instant.EPOCH);

        assertThat(period.start()).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
        assertThat(period.end()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
    }

    private static Subscription monthlySubscription(Instant periodStart, Instant periodEnd) {
        Plan plan = Plan.builder()
                .code("PRO")
                .name("Professional")
                .billingCycle(BillingCycle.MONTHLY)
                .basePrice(new BigDecimal("99.0000"))
                .currency("USD")
                .trialDays(0)
                .active(true)
                .build();

        return Subscription.builder()
                .plan(plan)
                .quantity(1)
                .startedAt(periodStart)
                .currentPeriodStart(periodStart)
                .currentPeriodEnd(periodEnd)
                .build();
    }
}
