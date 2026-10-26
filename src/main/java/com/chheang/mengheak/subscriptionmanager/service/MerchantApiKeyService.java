package com.chheang.mengheak.subscriptionmanager.service;

import com.chheang.mengheak.subscriptionmanager.common.PageResponse;
import com.chheang.mengheak.subscriptionmanager.dto.merchant.MerchantDtos.*;
import com.chheang.mengheak.subscriptionmanager.entity.MerchantApiKey;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface MerchantApiKeyService {
  CreatedKey create(KeyRequest request);

  PageResponse<KeyResponse> list(Pageable pageable);

  void revoke(UUID id);

  MerchantApiKey authenticate(String secret);
}
