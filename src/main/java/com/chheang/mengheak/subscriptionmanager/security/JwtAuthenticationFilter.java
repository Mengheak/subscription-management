package com.chheang.mengheak.subscriptionmanager.security;

import com.chheang.mengheak.subscriptionmanager.common.constant.ApiPaths;
import com.chheang.mengheak.subscriptionmanager.common.constant.SecurityConstants;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;
    private final RestAuthenticationEntryPoint entryPoint;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        TenantContextHolder.clear();
        try {
            String path = request.getServletPath();
            boolean credentialsEndpoint = path.equals(ApiPaths.AUTH + "/login")
                    || path.equals(ApiPaths.AUTH + "/register");
            String header = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);
            if (!credentialsEndpoint && header != null && header.startsWith(SecurityConstants.BEARER_PREFIX)) {
                AppUserPrincipal principal;
                try {
                    String token = header.substring(SecurityConstants.BEARER_PREFIX.length()).trim();
                    principal = currentUserService.resolve(jwtService.parse(token));
                } catch (JwtException | IllegalArgumentException | AuthenticationException ex) {
                    SecurityContextHolder.clearContext();
                    entryPoint.commence(request, response, new BadCredentialsException("Invalid bearer token", ex));
                    return;
                }
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
                TenantContextHolder.setTenantId(principal.tenantId());
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
