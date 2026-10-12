package com.chheang.mengheak.subscriptionmanager.controller;

import com.chheang.mengheak.subscriptionmanager.common.ApiResponse;
import com.chheang.mengheak.subscriptionmanager.common.constant.ApiPaths;
import com.chheang.mengheak.subscriptionmanager.dto.auth.AuthResponse;
import com.chheang.mengheak.subscriptionmanager.dto.auth.LoginRequest;
import com.chheang.mengheak.subscriptionmanager.dto.auth.RegisterTenantRequest;
import com.chheang.mengheak.subscriptionmanager.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.AUTH)
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Authentication", description = "Tenant registration and token issuance")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(
            summary = "Register a tenant",
            description = """
                    Creates the organisation together with its first administrator and returns an
                    access token for that administrator, so onboarding completes in one call.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tenant created and token issued"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Slug or e-mail already taken", content = @Content)
    })
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterTenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(authService.register(request)));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Exchange credentials for an access token",
            description = """
                    The returned token carries the tenant claim that scopes every subsequent
                    request. Send it as `Authorization: Bearer <token>`.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token issued"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Tenant is suspended or closed", content = @Content)
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }
}
