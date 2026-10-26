package com.chheang.mengheak.subscriptionmanager.service;

import com.chheang.mengheak.subscriptionmanager.common.enums.SubscriptionStatus;
import com.chheang.mengheak.subscriptionmanager.entity.Subscription;
import com.chheang.mengheak.subscriptionmanager.dto.subscription.SubscriptionResponse;
import com.chheang.mengheak.subscriptionmanager.exception.TenantContextMissingException;
import com.chheang.mengheak.subscriptionmanager.mapper.SubscriptionMapper;
import com.chheang.mengheak.subscriptionmanager.repository.SubscriptionRepository;
import com.chheang.mengheak.subscriptionmanager.security.TenantContextHolder;
import com.chheang.mengheak.subscriptionmanager.service.impl.SubscriptionServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {
    @Mock SubscriptionRepository repository;
    @Mock SubscriptionMapper mapper;
    @InjectMocks SubscriptionServiceImpl service;

    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void currentSubscriptionUsesAuthenticatedTenantAndOnlyLiveStatuses() {
        UUID tenant = UUID.randomUUID();
        Subscription subscription = Subscription.builder().tenantId(tenant).status(SubscriptionStatus.ACTIVE).build();
        when(repository.findFirstByTenantIdAndStatusIn(tenant, SubscriptionStatus.liveStatuses()))
                .thenReturn(Optional.of(subscription));
        SubscriptionResponse response = mock(SubscriptionResponse.class);
        when(mapper.toResponse(subscription)).thenReturn(response);
        assertThat(TenantContextHolder.runAs(tenant, service::current)).isSameAs(response);
        verify(repository).findFirstByTenantIdAndStatusIn(tenant, SubscriptionStatus.liveStatuses());
        verify(mapper).toResponse(subscription);
        verifyNoMoreInteractions(repository);
        assertThat(TenantContextHolder.isSet()).isFalse();
    }

    @Test void noCurrentSubscriptionIsAnEmptyResult() {
        UUID tenant = UUID.randomUUID();
        when(repository.findFirstByTenantIdAndStatusIn(tenant, SubscriptionStatus.liveStatuses()))
                .thenReturn(Optional.empty());
        assertThat(TenantContextHolder.runAs(tenant, service::current)).isNull();
        verifyNoInteractions(mapper);
    }

    @Test void missingTenantCannotReadSubscriptions() {
        assertThatThrownBy(service::current).isInstanceOf(TenantContextMissingException.class);
        verifyNoInteractions(repository, mapper);
    }
}
