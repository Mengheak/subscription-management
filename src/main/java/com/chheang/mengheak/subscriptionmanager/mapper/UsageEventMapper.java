package com.chheang.mengheak.subscriptionmanager.mapper;

import com.chheang.mengheak.subscriptionmanager.dto.usage.RecordUsageRequest;
import com.chheang.mengheak.subscriptionmanager.dto.usage.UsageAcceptedResponse;
import com.chheang.mengheak.subscriptionmanager.messaging.event.UsageEventMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.UUID;

@Mapper(config = CentralMapperConfig.class)
public interface UsageEventMapper {

    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "subscriptionId", source = "subscriptionId")
    @Mapping(target = "metric", source = "request.metric")
    @Mapping(target = "quantity", source = "request.quantity")
    @Mapping(target = "source", source = "request.source")
    @Mapping(target = "occurredAt", source = "occurredAt")
    @Mapping(target = "idempotencyKey", source = "idempotencyKey")
    UsageEventMessage toMessage(RecordUsageRequest request,
                                UUID tenantId,
                                UUID subscriptionId,
                                Instant occurredAt,
                                String idempotencyKey);

    @Mapping(target = "subscriptionId", source = "message.subscriptionId")
    @Mapping(target = "metric", source = "message.metric")
    @Mapping(target = "quantity", source = "message.quantity")
    @Mapping(target = "occurredAt", source = "message.occurredAt")
    @Mapping(target = "idempotencyKey", source = "message.idempotencyKey")
    @Mapping(target = "topic", source = "topic")
    @Mapping(target = "partition", source = "partition")
    @Mapping(target = "offset", source = "offset")
    UsageAcceptedResponse toAcceptedResponse(UsageEventMessage message,
                                             String topic,
                                             int partition,
                                             long offset);
}
