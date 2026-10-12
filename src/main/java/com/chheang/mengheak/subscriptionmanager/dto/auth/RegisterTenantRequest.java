package com.chheang.mengheak.subscriptionmanager.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterTenantRequest", description = "Creates a tenant together with its first administrator")
public record RegisterTenantRequest(

        @Schema(description = "Display name of the organisation", example = "Acme Corporation")
        @NotBlank @Size(max = 150)
        String tenantName,

        @Schema(description = "URL-safe unique handle", example = "acme-corp")
        @NotBlank
        @Size(min = 3, max = 80)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "must be lowercase alphanumeric words separated by single hyphens")
        String slug,

        @Schema(description = "Administrator e-mail, also used as the login", example = "admin@acme.example")
        @NotBlank @Email @Size(max = 255)
        String adminEmail,

        @Schema(description = "Administrator full name", example = "Dara Sok")
        @NotBlank @Size(max = 150)
        String adminFullName,

        @Schema(description = "Administrator password", example = "S3cure-Passw0rd!")
        @NotBlank @Size(min = 12, max = 72,
                message = "must be between 12 and 72 characters (BCrypt truncates beyond 72 bytes)")
        String adminPassword
) {
}
