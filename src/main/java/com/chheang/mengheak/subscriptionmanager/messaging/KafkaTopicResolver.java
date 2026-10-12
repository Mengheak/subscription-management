package com.chheang.mengheak.subscriptionmanager.messaging;

import com.chheang.mengheak.subscriptionmanager.config.properties.KafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("kafkaTopics")
@RequiredArgsConstructor
public class KafkaTopicResolver {

    private final KafkaTopicProperties properties;

    public String usageEvents() {
        return properties.usageEvents().name();
    }

    public String usageEventsDeadLetter() {
        return properties.usageEventsDeadLetterTopic();
    }
}
