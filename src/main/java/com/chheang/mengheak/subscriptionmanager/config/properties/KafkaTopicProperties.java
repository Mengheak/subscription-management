package com.chheang.mengheak.subscriptionmanager.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.kafka")
public record KafkaTopicProperties(

        @NestedConfigurationProperty @Valid @NotNull Topic usageEvents,

        @NotBlank String deadLetterSuffix,

        @Min(1) int maxDeliveryAttempts,

        @NotNull Duration retryBackoff
) {

    public record Topic(
            @NotBlank String name,
            @Min(1) int partitions,
            @Min(1) short replicas,
            @NotNull Duration retention
    ) {
    }

    public String usageEventsDeadLetterTopic() {
        return usageEvents.name() + deadLetterSuffix;
    }
}
