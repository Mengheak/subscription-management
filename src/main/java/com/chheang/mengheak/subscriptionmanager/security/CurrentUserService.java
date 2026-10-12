package com.chheang.mengheak.subscriptionmanager.security;

import com.chheang.mengheak.subscriptionmanager.repository.AppUserRepository;
import com.chheang.mengheak.subscriptionmanager.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final AppUserRepository users;
    private final TenantRepository tenants;

    @Transactional(readOnly = true)
    public AppUserPrincipal resolve(AppUserPrincipal claims) {
        var user = users.findByIdAndTenantId(claims.userId(), claims.tenantId())
                .filter(candidate -> candidate.isEnabled())
                .orElseThrow(() -> new BadCredentialsException("Account is unavailable"));
        if (tenants.findById(claims.tenantId()).filter(tenant -> tenant.isActive()).isEmpty()) {
            throw new BadCredentialsException("Account is unavailable");
        }
        return AppUserPrincipal.fromToken(user.getId(), user.getTenantId(), user.getEmail(), user.getRole());
    }
}
