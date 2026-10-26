package com.chheang.mengheak.subscriptionmanager.property;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(

        @NestedConfigurationProperty @Valid @NotNull Jwt jwt,

        @NotEmpty List<String> publicPaths
) {

    public record Jwt(
            @NotBlank String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration clockSkew,
            @NotBlank String tenantClaim,
            @NotBlank String rolesClaim
    ) {
    }

    public String[] publicPathsArray() {
        return publicPaths.toArray(String[]::new);
    }
}
