package com.portfolio.taskflow.controller;

import com.portfolio.taskflow.dto.AuthResponse;
import com.portfolio.taskflow.dto.LoginRequest;
import com.portfolio.taskflow.dto.RegistrationRequest;
import com.portfolio.taskflow.exception.RequestValidationException;
import com.portfolio.taskflow.service.AuthService;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@RequestBody @Valid RegistrationRequest request, BindingResult validation) {
        validatePassword(request.password(), validation);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(auth.register(request.email(), request.password(), request.claimCode()));
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request, BindingResult validation) {
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
            throw new RequestValidationException("Les données d’authentification sont invalides.",
                    validation.getFieldErrors());
        }
    }
}
