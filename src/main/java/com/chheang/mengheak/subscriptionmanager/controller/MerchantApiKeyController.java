package com.chheang.mengheak.subscriptionmanager.controller;

import com.chheang.mengheak.subscriptionmanager.common.*;
import com.chheang.mengheak.subscriptionmanager.dto.merchant.MerchantDtos.*;
import com.chheang.mengheak.subscriptionmanager.service.MerchantApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchant/api-keys")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT_ADMIN')")
@Tag(
    name = "Merchant API keys",
    description = "Human administrator access only. Keys cannot create more keys.")
public class MerchantApiKeyController {
  private final MerchantApiKeyService service;

  @GetMapping
  public ApiResponse<PageResponse<KeyResponse>> list(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable page) {
    return ApiResponse.ok(service.list(page));
  }

  @PostMapping
  @Operation(
      summary = "Create API key",
      description =
          "Returns the secret once. Store it in your backend secret manager. Only a SHA-256 hash is"
              + " retained.")
  public ResponseEntity<ApiResponse<CreatedKey>> create(@Valid @RequestBody KeyRequest r) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.ok(service.create(r)));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> revoke(@PathVariable UUID id) {
    service.revoke(id);
    return new ApiResponse<>(true, "API key revoked", null);
  }
}
