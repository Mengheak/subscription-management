package com.chheang.mengheak.subscriptionmanager.config.properties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Validated
@ConfigurationProperties(prefix = "app.billing")
public record BillingProperties(

        @NotBlank @Size(min = 3, max = 3) String defaultCurrency,

        @Min(0) @Max(6) int moneyScale,

        @Min(0) @Max(8) int usageScale,

        @NotNull RoundingMode roundingMode,

        boolean prorationEnabled,

        @Min(0) int invoiceDueDays,

        @NotBlank String invoiceNumberPrefix,

        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal taxRate
) {

    public BigDecimal toMoney(BigDecimal amount) {
        return amount.setScale(moneyScale, roundingMode);
    }

    public BigDecimal toQuantity(BigDecimal quantity) {
        return quantity.setScale(usageScale, roundingMode);
    }
}
