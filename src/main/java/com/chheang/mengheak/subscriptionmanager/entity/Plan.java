package com.chheang.mengheak.subscriptionmanager.entity;

import com.chheang.mengheak.subscriptionmanager.common.enums.BillingCycle;
import com.chheang.mengheak.subscriptionmanager.common.enums.UsageMetric;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "plans")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true, exclude = "meteredRates")
public class Plan extends BaseEntity {

    @Column(name = "code", nullable = false, length = 60, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    @Column(name = "base_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal basePrice;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "trial_days", nullable = false)
    private int trialDays;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Builder.Default
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PlanMeteredRate> meteredRates = new ArrayList<>();

    public Optional<PlanMeteredRate> rateFor(UsageMetric metric) {
        return meteredRates.stream()
                .filter(rate -> rate.getMetric() == metric)
                .findFirst();
    }

    public void addMeteredRate(PlanMeteredRate rate) {
        meteredRates.add(rate);
        rate.setPlan(this);
    }
}
