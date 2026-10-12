package com.chheang.mengheak.subscriptionmanager.service;

import com.chheang.mengheak.subscriptionmanager.messaging.event.UsageEventMessage;

public interface UsageIngestionService {

    void ingest(UsageEventMessage message);
}
