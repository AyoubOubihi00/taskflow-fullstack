package com.portfolio.taskflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record RegistrationRequest(
        @NotBlank(message = "L’adresse e-mail est obligatoire.")
        @Email(message = "L’adresse e-mail est invalide.")
        @Size(max = 254, message = "L’adresse e-mail est limitée à 254 caractères.") String email,
        @NotBlank(message = "Le mot de passe est obligatoire.")
        @Size(min = 12, max = 72, message = "Le mot de passe doit contenir entre 12 et 72 caractères.") String password,
        @Size(max = 128, message = "Le code d’attribution est limité à 128 caractères.") String claimCode
) {
    public RegistrationRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "RegistrationRequest";
    }
}
