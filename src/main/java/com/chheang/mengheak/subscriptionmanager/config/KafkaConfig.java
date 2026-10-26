package com.chheang.mengheak.subscriptionmanager.config;

import com.chheang.mengheak.subscriptionmanager.property.KafkaTopicProperties;
import com.chheang.mengheak.subscriptionmanager.messaging.UsageEventConsumer;
import com.chheang.mengheak.subscriptionmanager.messaging.event.UsageEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.KafkaException;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;
    private final KafkaTopicProperties topicProperties;

    @Bean
    public NewTopic usageEventsTopic() {
        return TopicBuilder.name(topicProperties.usageEvents().name())
                .partitions(topicProperties.usageEvents().partitions())
                .replicas(topicProperties.usageEvents().replicas())
                .config(TopicConfig.RETENTION_MS_CONFIG,
                        String.valueOf(topicProperties.usageEvents().retention().toMillis()))
                .build();
    }

    @Bean
    public NewTopic usageEventsDeadLetterTopic() {
        return TopicBuilder.name(topicProperties.usageEventsDeadLetterTopic())
                .partitions(1)
                .replicas(topicProperties.usageEvents().replicas())
                .config(TopicConfig.RETENTION_MS_CONFIG,
                        String.valueOf(topicProperties.usageEvents().retention().toMillis()))
                .build();
    }

    @Bean
    public ProducerFactory<String, UsageEventMessage> usageEventProducerFactory() {
        Map<String, Object> config = kafkaProperties.buildProducerProperties(null);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, UsageEventMessage> usageEventKafkaTemplate(
            ProducerFactory<String, UsageEventMessage> usageEventProducerFactory) {
        return new KafkaTemplate<>(usageEventProducerFactory);
    }

    @Bean
    public ConsumerFactory<String, UsageEventMessage> usageEventConsumerFactory() {
        Map<String, Object> config = kafkaProperties.buildConsumerProperties(null);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, UsageEventMessage.class.getName());
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UsageEventMessage> usageEventListenerContainerFactory(
            ConsumerFactory<String, UsageEventMessage> usageEventConsumerFactory,
            DefaultErrorHandler usageEventErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, UsageEventMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(usageEventConsumerFactory);
        factory.setCommonErrorHandler(usageEventErrorHandler);
        factory.setConcurrency(kafkaProperties.getListener().getConcurrency() == null
                ? 1 : kafkaProperties.getListener().getConcurrency());

        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    @Bean
    public DefaultErrorHandler usageEventErrorHandler(KafkaTemplate<Object, Object> deadLetterKafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                deadLetterKafkaTemplate,
                (record, exception) -> new TopicPartition(topicProperties.usageEventsDeadLetterTopic(), -1));

        FixedBackOff backOff = new FixedBackOff(
                topicProperties.retryBackoff().toMillis(),
                Math.max(0, topicProperties.maxDeliveryAttempts() - 1L));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(UsageEventConsumer.NonRetryableUsageEventException.class);
        errorHandler.setLogLevel(KafkaException.Level.WARN);
        return errorHandler;
    }

    @Bean
    public KafkaTemplate<Object, Object> deadLetterKafkaTemplate() {
        Map<String, Object> config = kafkaProperties.buildProducerProperties(null);
        config.remove(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG);
        config.remove(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);

        Map<Class<?>, Serializer<?>> keyDelegates = new LinkedHashMap<>();
        keyDelegates.put(byte[].class, new ByteArraySerializer());
        keyDelegates.put(String.class, new StringSerializer());

        Map<Class<?>, Serializer<?>> valueDelegates = new LinkedHashMap<>();
        valueDelegates.put(byte[].class, new ByteArraySerializer());
        valueDelegates.put(UsageEventMessage.class, new JsonSerializer<UsageEventMessage>());

        DefaultKafkaProducerFactory<Object, Object> factory = new DefaultKafkaProducerFactory<>(
                config,
                new DelegatingByTypeSerializer(keyDelegates),
                new DelegatingByTypeSerializer(valueDelegates));

        return new KafkaTemplate<>(factory);
    }
}
