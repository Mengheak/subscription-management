package com.chheang.mengheak.subscriptionmanager.security;

import com.chheang.mengheak.subscriptionmanager.service.MerchantApiKeyService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class MerchantApiKeyFilter extends OncePerRequestFilter {
  private final MerchantApiKeyService keys;
  private final RestAuthenticationEntryPoint entryPoint;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String secret = request.getHeader("X-API-Key");
    if (secret == null) {
      chain.doFilter(request, response);
      return;
    }
    try {
      if (!request.getServletPath().startsWith("/api/v1/merchant/")
          || SecurityContextHolder.getContext().getAuthentication() != null)
        throw new BadCredentialsException(
            "Use an API key only on merchant routes, without a bearer token");
      var key = keys.authenticate(secret);
      var auth =
          UsernamePasswordAuthenticationToken.authenticated(
              new MerchantApiPrincipal(key.getId(), key.getTenantId()),
              null,
              key.getScopes().stream()
                  .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                  .toList());
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(auth);
      SecurityContextHolder.setContext(context);
      TenantContextHolder.setTenantId(key.getTenantId());
    } catch (AuthenticationException e) {
      SecurityContextHolder.clearContext();
      TenantContextHolder.clear();
      entryPoint.commence(request, response, e);
      return;
    }
    try {
      chain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
      TenantContextHolder.clear();
    }
  }
}
