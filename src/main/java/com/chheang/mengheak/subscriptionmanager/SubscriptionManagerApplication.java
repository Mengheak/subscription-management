package com.chheang.mengheak.subscriptionmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SubscriptionManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubscriptionManagerApplication.class, args);
    }
}
