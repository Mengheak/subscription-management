package com.chheang.mengheak.subscriptionmanager.security;

import com.chheang.mengheak.subscriptionmanager.common.enums.UserRole;
import com.chheang.mengheak.subscriptionmanager.entity.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record AppUserPrincipal(
        UUID userId,
        UUID tenantId,
        String email,
        String passwordHash,
        UserRole role,
        boolean enabled
) implements UserDetails {

    public static AppUserPrincipal forAuthentication(AppUser user) {
        return new AppUserPrincipal(
                user.getId(), user.getTenantId(), user.getEmail(),
                user.getPasswordHash(), user.getRole(), user.isEnabled());
    }

    public static AppUserPrincipal fromToken(UUID userId, UUID tenantId, String email, UserRole role) {
        return new AppUserPrincipal(userId, tenantId, email, null, role, true);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return "AppUserPrincipal[userId=%s, tenantId=%s, role=%s]".formatted(userId, tenantId, role);
    }
}
