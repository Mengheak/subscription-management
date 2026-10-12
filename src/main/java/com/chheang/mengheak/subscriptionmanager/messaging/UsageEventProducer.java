package com.chheang.mengheak.subscriptionmanager.messaging;

import com.chheang.mengheak.subscriptionmanager.common.exception.ApplicationException;
import com.chheang.mengheak.subscriptionmanager.config.properties.KafkaTopicProperties;
import com.chheang.mengheak.subscriptionmanager.messaging.event.UsageEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class UsageEventProducer {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, UsageEventMessage> usageEventKafkaTemplate;
    private final KafkaTopicProperties topicProperties;

    public SendResult<String, UsageEventMessage> publish(UsageEventMessage message) {
        String topic = topicProperties.usageEvents().name();
        ProducerRecord<String, UsageEventMessage> record =
                new ProducerRecord<>(topic, message.partitionKey(), message);

        try {
            SendResult<String, UsageEventMessage> result =
                    usageEventKafkaTemplate.send(record).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            log.debug("Published usage event tenant={} metric={} key={} to {}-{}@{}",
                    message.tenantId(), message.metric(), message.idempotencyKey(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            return result;

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PublishFailedException("Interrupted while publishing the usage event");
        } catch (ExecutionException | TimeoutException ex) {
            log.error("Failed to publish usage event tenant={} key={}",
                    message.tenantId(), message.idempotencyKey(), ex);
            throw new PublishFailedException(
                    "Usage event could not be published; retry with the same idempotency key");
        }
    }

    public static class PublishFailedException extends ApplicationException {

        public PublishFailedException(String message) {
            super(HttpStatus.SERVICE_UNAVAILABLE, "USAGE_PUBLISH_FAILED", message);
        }
    }
}
