package com.chheang.mengheak.subscriptionmanager.messaging;

import com.chheang.mengheak.subscriptionmanager.messaging.event.UsageEventMessage;
import com.chheang.mengheak.subscriptionmanager.security.TenantContextHolder;
import com.chheang.mengheak.subscriptionmanager.service.UsageIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class UsageEventConsumer {

    private final UsageIngestionService usageIngestionService;

    @KafkaListener(
            topics = "#{@kafkaTopics.usageEvents()}",
            containerFactory = "usageEventListenerContainerFactory"
    )
    public void onUsageEvent(@Payload UsageEventMessage message,
                             @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                             @Header(KafkaHeaders.OFFSET) long offset) {

        validate(message, partition, offset);

        TenantContextHolder.setTenantId(message.tenantId());
        try {
            usageIngestionService.ingest(message);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void validate(UsageEventMessage message, int partition, long offset) {
        if (message.tenantId() == null || message.subscriptionId() == null || message.metric() == null) {
            throw new NonRetryableUsageEventException(
                    "Usage event at partition=%d offset=%d is missing tenant, subscription or metric"
                            .formatted(partition, offset));
        }
        if (message.quantity() == null || message.quantity().compareTo(BigDecimal.ZERO) < 0) {
            throw new NonRetryableUsageEventException(
                    "Usage event at partition=%d offset=%d has a null or negative quantity"
                            .formatted(partition, offset));
        }
        if (message.occurredAt() == null) {
            throw new NonRetryableUsageEventException(
                    "Usage event at partition=%d offset=%d has no occurredAt timestamp"
                            .formatted(partition, offset));
        }
    }

    public static class NonRetryableUsageEventException extends RuntimeException {

        public NonRetryableUsageEventException(String message) {
            super(message);
        }
    }
}
