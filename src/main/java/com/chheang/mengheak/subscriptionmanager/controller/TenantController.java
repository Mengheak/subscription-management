package com.chheang.mengheak.subscriptionmanager.controller;

import com.chheang.mengheak.subscriptionmanager.common.ApiResponse;
import com.chheang.mengheak.subscriptionmanager.common.constant.ApiPaths;
import com.chheang.mengheak.subscriptionmanager.dto.tenant.TenantResponse;
import com.chheang.mengheak.subscriptionmanager.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.TENANTS)
@RequiredArgsConstructor
@Tag(name = "Tenants", description = "The calling tenant's own profile")
public class TenantController {

    private final TenantService tenantService;

    @GetMapping("/me")
    @Operation(
            summary = "Get the calling tenant",
            description = """
                    Resolved from the token's tenant claim. There is no endpoint to fetch a tenant
                    by id - a caller can only ever see its own.""")
    public ResponseEntity<ApiResponse<TenantResponse>> currentTenant() {
        return ResponseEntity.ok(ApiResponse.ok(tenantService.currentTenant()));
    }
}
