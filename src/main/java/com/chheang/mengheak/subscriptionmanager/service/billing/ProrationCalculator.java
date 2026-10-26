package com.chheang.mengheak.subscriptionmanager.service.billing;

import com.chheang.mengheak.subscriptionmanager.property.BillingProperties;
import com.chheang.mengheak.subscriptionmanager.entity.Plan;
import com.chheang.mengheak.subscriptionmanager.entity.Subscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ProrationCalculator {

    private static final MathContext RATIO_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);

    private final BillingProperties billingProperties;

    public ProrationResult calculate(Subscription subscription,
                                     Plan newPlan,
                                     int newQuantity,
                                     Instant effectiveAt) {

        Instant periodStart = subscription.getCurrentPeriodStart();
        Instant periodEnd = subscription.getCurrentPeriodEnd();

        BigDecimal remainingRatio = remainingRatio(periodStart, periodEnd, effectiveAt);

        BigDecimal oldPeriodValue = subscription.getPlan().getBasePrice()
                .multiply(BigDecimal.valueOf(subscription.getQuantity()));
        BigDecimal newPeriodValue = newPlan.getBasePrice()
                .multiply(BigDecimal.valueOf(newQuantity));

        BigDecimal credit = billingProperties.toMoney(oldPeriodValue.multiply(remainingRatio));
        BigDecimal charge = billingProperties.toMoney(newPeriodValue.multiply(remainingRatio));

        return new ProrationResult(
                credit,
                charge,
                charge.subtract(credit),
                remainingRatio,
                effectiveAt,
                periodEnd);
    }

    private BigDecimal remainingRatio(Instant periodStart, Instant periodEnd, Instant effectiveAt) {
        if (!effectiveAt.isBefore(periodEnd)) {
            return BigDecimal.ZERO;
        }
        if (!effectiveAt.isAfter(periodStart)) {
            return BigDecimal.ONE;
        }

        long totalSeconds = Duration.between(periodStart, periodEnd).getSeconds();
        if (totalSeconds <= 0) {
            return BigDecimal.ZERO;
        }
        long remainingSeconds = Duration.between(effectiveAt, periodEnd).getSeconds();

        return BigDecimal.valueOf(remainingSeconds)
                .divide(BigDecimal.valueOf(totalSeconds), RATIO_CONTEXT);
    }

    public record ProrationResult(
            BigDecimal creditAmount,
            BigDecimal chargeAmount,
            BigDecimal netAmount,
            BigDecimal remainingRatio,
            Instant effectiveAt,
            Instant periodEnd
    ) {

        public boolean isCharge() {
            return netAmount.signum() > 0;
        }

        public boolean isZero() {
            return creditAmount.signum() == 0 && chargeAmount.signum() == 0;
        }
    }
}
