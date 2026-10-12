package com.chheang.mengheak.subscriptionmanager.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "Credentials exchanged for an access token")
public record LoginRequest(

        @Schema(example = "admin@acme.example")
        @NotBlank @Email
        String email,

        @Schema(example = "S3cure-Passw0rd!")
        @NotBlank
        String password
) {
}
