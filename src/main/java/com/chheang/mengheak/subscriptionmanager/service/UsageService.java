package com.chheang.mengheak.subscriptionmanager.service;

import com.chheang.mengheak.subscriptionmanager.dto.usage.RecordUsageRequest;
import com.chheang.mengheak.subscriptionmanager.dto.usage.UsageAcceptedResponse;
import com.chheang.mengheak.subscriptionmanager.dto.usage.UsageSummaryResponse;

import java.util.UUID;

public interface UsageService {

    UsageAcceptedResponse record(RecordUsageRequest request);

    UsageSummaryResponse currentPeriodSummary(UUID subscriptionId);
}
