package com.chheang.mengheak.subscriptionmanager.service.impl;

import com.chheang.mengheak.subscriptionmanager.common.constant.SecurityConstants;
import com.chheang.mengheak.subscriptionmanager.common.enums.TenantStatus;
import com.chheang.mengheak.subscriptionmanager.common.enums.UserRole;
import com.chheang.mengheak.subscriptionmanager.common.exception.BusinessRuleException;
import com.chheang.mengheak.subscriptionmanager.common.exception.DuplicateResourceException;
import com.chheang.mengheak.subscriptionmanager.common.exception.ResourceNotFoundException;
import com.chheang.mengheak.subscriptionmanager.dto.auth.AuthResponse;
import com.chheang.mengheak.subscriptionmanager.dto.auth.LoginRequest;
import com.chheang.mengheak.subscriptionmanager.dto.auth.RegisterTenantRequest;
import com.chheang.mengheak.subscriptionmanager.entity.AppUser;
import com.chheang.mengheak.subscriptionmanager.entity.Tenant;
import com.chheang.mengheak.subscriptionmanager.repository.AppUserRepository;
import com.chheang.mengheak.subscriptionmanager.repository.TenantRepository;
import com.chheang.mengheak.subscriptionmanager.security.AppUserPrincipal;
import com.chheang.mengheak.subscriptionmanager.security.JwtService;
import com.chheang.mengheak.subscriptionmanager.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final TenantRepository tenantRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthResponse register(RegisterTenantRequest request) {
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("Tenant slug", request.slug());
        }
        if (appUserRepository.existsByEmailIgnoreCase(request.adminEmail())) {
            throw new DuplicateResourceException("User e-mail", request.adminEmail());
        }

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name(request.tenantName())
                .slug(request.slug())
                .contactEmail(request.adminEmail())
                .status(TenantStatus.ACTIVE)
                .build());

        AppUser admin = appUserRepository.save(AppUser.builder()
                .tenantId(tenant.getId())
                .email(request.adminEmail())
                .passwordHash(passwordEncoder.encode(request.adminPassword()))
                .fullName(request.adminFullName())
                .role(UserRole.TENANT_ADMIN)
                .enabled(true)
                .build());

        log.info("Registered tenant {} ({}) with administrator {}",
                tenant.getSlug(), tenant.getId(), admin.getId());

        return issueToken(AppUserPrincipal.forAuthentication(admin), tenant);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();

        Tenant tenant = tenantRepository.findById(principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", principal.tenantId()));

        if (!tenant.isActive()) {
            throw new BusinessRuleException("TENANT_INACTIVE",
                    "Tenant '%s' is %s and cannot authenticate".formatted(tenant.getSlug(), tenant.getStatus()));
        }

        log.debug("Issued token for user {} of tenant {}", principal.userId(), tenant.getSlug());
        return issueToken(principal, tenant);
    }

    private AuthResponse issueToken(AppUserPrincipal principal, Tenant tenant) {
        JwtService.IssuedToken token = jwtService.issue(principal);
        return new AuthResponse(
                token.token(),
                SecurityConstants.TOKEN_TYPE,
                token.expiresAt(),
                principal.userId(),
                tenant.getId(),
                tenant.getSlug(),
                principal.role());
    }
}
