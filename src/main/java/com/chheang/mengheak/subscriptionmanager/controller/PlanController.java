package com.chheang.mengheak.subscriptionmanager.controller;

import com.chheang.mengheak.subscriptionmanager.common.ApiResponse;
import com.chheang.mengheak.subscriptionmanager.common.constant.ApiPaths;
import com.chheang.mengheak.subscriptionmanager.dto.plan.PlanResponse;
import com.chheang.mengheak.subscriptionmanager.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(ApiPaths.PLANS)
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Plans", description = "Public plan catalogue")
public class PlanController {

    private final PlanService planService;

    @GetMapping
    @Operation(summary = "List active plans", description = "Ordered by base price, cheapest first.")
    public ResponseEntity<ApiResponse<List<PlanResponse>>> listActivePlans() {
        return ResponseEntity.ok(ApiResponse.ok(planService.listActivePlans()));
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get a plan by its catalogue code")
    public ResponseEntity<ApiResponse<PlanResponse>> getPlan(
            @Parameter(description = "Catalogue code", example = "PRO")
            @PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(planService.getByCode(code)));
    }
}
