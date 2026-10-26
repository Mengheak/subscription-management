package com.chheang.mengheak.subscriptionmanager.repository;

import com.chheang.mengheak.subscriptionmanager.entity.MerchantApiKey;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantApiKeyRepository extends JpaRepository<MerchantApiKey, UUID> {
  Optional<MerchantApiKey> findByTokenHash(String hash);

  Optional<MerchantApiKey> findByIdAndTenantId(UUID id, UUID tenantId);

  Page<MerchantApiKey> findAllByTenantId(UUID tenantId, Pageable pageable);

  long countByTenantIdAndRevokedAtIsNullAndExpiresAtAfter(UUID tenantId, Instant now);
}
