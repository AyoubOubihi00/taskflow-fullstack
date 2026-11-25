package com.portfolio.taskflow.dto;

import java.time.Instant;

public record AuthResponse(String accessToken, Instant expiresAt, String email) {

    @Override
    public String toString() {
        return "AuthResponse";
    }
}
