package com.chheang.mengheak.subscriptionmanager.security;

import com.chheang.mengheak.subscriptionmanager.common.enums.UserRole;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    private final JwtService jwt = mock(JwtService.class);
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final RestAuthenticationEntryPoint entry = mock(RestAuthenticationEntryPoint.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, users, entry);

    @AfterEach void clean() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test void downstreamFailureIsNotRetriedAsInvalidToken() throws Exception {
        var principal = AppUserPrincipal.fromToken(UUID.randomUUID(), UUID.randomUUID(), "test@example.com", UserRole.TENANT_ADMIN);
        when(jwt.parse("token")).thenReturn(principal);
        when(users.resolve(principal)).thenReturn(principal);
        var request = request("token");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertThat(TenantContextHolder.requireTenantId()).isEqualTo(principal.tenantId());
            throw new IllegalArgumentException("domain failure");
        }).when(chain).doFilter(request, response);
        assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isInstanceOf(IllegalArgumentException.class);
        verify(chain, times(1)).doFilter(request, response);
        verifyNoInteractions(entry);
        assertThat(TenantContextHolder.isSet()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test void invalidTokenStopsBeforeController() throws Exception {
        when(jwt.parse("invalid")).thenThrow(new IllegalArgumentException("bad token"));
        var request = request("invalid");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        verifyNoInteractions(chain, users);
        verify(entry).commence(eq(request), eq(response), any());
        assertThat(TenantContextHolder.isSet()).isFalse();
    }

    @Test void credentialEndpointsDoNotInheritBearerTenant() throws Exception {
        var request = request("token");
        request.setServletPath("/api/v1/auth/register");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        TenantContextHolder.setTenantId(UUID.randomUUID());
        filter.doFilter(request, response, chain);
        verifyNoInteractions(jwt, users);
        verify(chain).doFilter(request, response);
        assertThat(TenantContextHolder.isSet()).isFalse();
    }

    private MockHttpServletRequest request(String token) {
        var request = new MockHttpServletRequest("GET", "/api/v1/tenants/me");
        request.setServletPath("/api/v1/tenants/me");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
