package com.portfolio.taskflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    ResponseEntity<JwtService.AuthResponse> register(@RequestBody @Valid Registration request, BindingResult validation) {
        validatePassword(request.password(), validation);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(auth.register(request.email(), request.password(), request.claimCode()));
    }

    @PostMapping("/login")
    ResponseEntity<JwtService.AuthResponse> login(@RequestBody @Valid Login request, BindingResult validation) {
        validatePassword(request.password(), validation);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(auth.login(request.email(), request.password()));
    }

    private static void validatePassword(String password, BindingResult validation) {
        if (password != null && !validation.hasFieldErrors("password")
                && password.getBytes(StandardCharsets.UTF_8).length > 72) {
            validation.rejectValue("password", "password.bytes", "Le mot de passe est limité à 72 octets UTF-8.");
        }
        if (validation.hasErrors()) {
            throw new ApiExceptionHandler.InvalidAuthInput(validation.getFieldErrors());
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    record Registration(
            @NotBlank(message = "L’adresse e-mail est obligatoire.")
            @Email(message = "L’adresse e-mail est invalide.")
            @Size(max = 254, message = "L’adresse e-mail est limitée à 254 caractères.") String email,
            @NotBlank(message = "Le mot de passe est obligatoire.")
            @Size(min = 12, max = 72, message = "Le mot de passe doit contenir entre 12 et 72 caractères.") String password,
            @Size(max = 128, message = "Le code d’attribution est limité à 128 caractères.") String claimCode
    ) {
        Registration {
            email = normalizeEmail(email);
        }

        @Override
        public String toString() {
            return "Registration";
        }
    }

    record Login(
            @NotBlank(message = "L’adresse e-mail est obligatoire.")
            @Email(message = "L’adresse e-mail est invalide.")
            @Size(max = 254, message = "L’adresse e-mail est limitée à 254 caractères.") String email,
            @NotBlank(message = "Le mot de passe est obligatoire.")
            @Size(max = 72, message = "Le mot de passe est limité à 72 caractères.") String password
    ) {
        Login {
            email = normalizeEmail(email);
        }

        @Override
        public String toString() {
            return "Login";
        }
    }
}
