package com.chheang.mengheak.subscriptionmanager.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI billingEngineOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Multi-Tenant SaaS Billing & Subscription Engine")
                        .version("v1")
                        .description("""
                                Subscription lifecycle, proration, invoicing and event-driven usage metering.

                                **Authentication.** Every endpoint except `/api/v1/auth/**` and the public plan
                                catalogue requires `Authorization: Bearer <token>`. The token carries the tenant
                                claim that scopes every query - there is no tenant header or path segment, by design.
                                """)
                        .contact(new Contact().name("Mengheak Chheang"))
                        .license(new License().name("Apache 2.0")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token issued by POST /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
