package com.chheang.mengheak.subscriptionmanager.service.impl;

import com.chheang.mengheak.subscriptionmanager.common.PageResponse;
import com.chheang.mengheak.subscriptionmanager.dto.merchant.MerchantDtos.*;
import com.chheang.mengheak.subscriptionmanager.entity.MerchantApiKey;
import com.chheang.mengheak.subscriptionmanager.exception.*;
import com.chheang.mengheak.subscriptionmanager.mapper.MerchantMapper;
import com.chheang.mengheak.subscriptionmanager.repository.*;
import com.chheang.mengheak.subscriptionmanager.security.TenantContextHolder;
import com.chheang.mengheak.subscriptionmanager.service.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantApiKeyServiceImpl implements MerchantApiKeyService {
  public static final Set<String> SCOPES =
      Set.of(
          "CATALOG_READ",
          "CUSTOMERS_READ",
          "CUSTOMERS_WRITE",
          "SUBSCRIPTIONS_READ",
          "SUBSCRIPTIONS_WRITE",
          "ACCESS_READ",
          "USAGE_WRITE",
          "INVOICES_READ");
  private final MerchantApiKeyRepository keys;
  private final TenantRepository tenants;
  private final MerchantAccess gate;
  private final MerchantMapper mapper;
  private final Clock clock;

  @Override
  @Transactional
  public CreatedKey create(KeyRequest request) {
    UUID t = gate.lock();
    if (!SCOPES.containsAll(request.scopes()))
      throw new BusinessRuleException("INVALID_SCOPE", "Choose supported merchant API scopes");
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String token = "sm_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var key =
        keys.save(
            MerchantApiKey.builder()
                .tenantId(t)
                .name(request.name().trim())
                .tokenHash(hash(token))
                .prefix(token.substring(0, 16))
                .scopes(Set.copyOf(request.scopes()))
                .expiresAt(clock.instant().plus(Duration.ofDays(request.expiresInDays())))
                .build());
    return new CreatedKey(mapper.key(key), token);
  }

  @Override
  public PageResponse<KeyResponse> list(Pageable page) {
    return PageResponse.from(
        keys.findAllByTenantId(TenantContextHolder.requireTenantId(), page), mapper::key);
  }

  @Override
  @Transactional
  public void revoke(UUID id) {
    UUID t = TenantContextHolder.requireTenantId();
    tenants.findLockedById(t).orElseThrow(() -> new ResourceNotFoundException("Workspace", t));
    var key =
        keys.findByIdAndTenantId(id, t)
            .orElseThrow(() -> new ResourceNotFoundException("API key", id));
    if (key.getRevokedAt() == null) key.setRevokedAt(clock.instant());
  }

  @Override
  public MerchantApiKey authenticate(String secret) {
    if (secret == null || !secret.matches("sm_live_[A-Za-z0-9_-]{43}")) throw invalid();
    var key =
        keys.findByTokenHash(hash(secret))
            .filter(k -> k.getRevokedAt() == null && k.getExpiresAt().isAfter(clock.instant()))
            .orElseThrow(MerchantApiKeyServiceImpl::invalid);
    if (!gate.unlocked(key.getTenantId())) throw invalid();
    return key;
  }

  public static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static BadCredentialsException invalid() {
    return new BadCredentialsException(
        "API key is unavailable, expired, revoked, or its platform subscription is inactive");
  }
}
