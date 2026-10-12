package com.chheang.mengheak.subscriptionmanager.service.billing;

import com.chheang.mengheak.subscriptionmanager.common.enums.BillingCycle;
import com.chheang.mengheak.subscriptionmanager.entity.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class BillingPeriodResolver {

    private static final int MAX_STEPS = 120;

    public BillingPeriod resolve(Subscription subscription, Instant at) {
        Instant start = subscription.getCurrentPeriodStart();
        Instant end = subscription.getCurrentPeriodEnd();
        BillingCycle cycle = subscription.getPlan().getBillingCycle();

        int steps = 0;
        while (at.isBefore(start) && steps++ < MAX_STEPS) {
            end = start;
            start = cycle.retreat(start);
        }
        while (!at.isBefore(end) && steps++ < MAX_STEPS) {
            start = end;
            end = cycle.advance(end);
        }

        if (steps >= MAX_STEPS) {
            log.warn("Usage timestamp {} is more than {} cycles from subscription {}'s current period; " +
                            "attributing it to the current period",
                    at, MAX_STEPS, subscription.getId());
            return new BillingPeriod(subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd());
        }
        return new BillingPeriod(start, end);
    }

    public record BillingPeriod(Instant start, Instant end) {

        public boolean contains(Instant instant) {
            return !instant.isBefore(start) && instant.isBefore(end);
        }
    }
}
