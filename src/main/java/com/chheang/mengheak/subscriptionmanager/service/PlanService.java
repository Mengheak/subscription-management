package com.chheang.mengheak.subscriptionmanager.service;

import com.chheang.mengheak.subscriptionmanager.dto.plan.PlanResponse;

import java.util.List;

public interface PlanService {

    List<PlanResponse> listActivePlans();

    PlanResponse getByCode(String code);
}
