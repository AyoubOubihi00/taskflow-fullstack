package com.portfolio.taskflow.security;

import com.portfolio.taskflow.dto.AuthResponse;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class JwtService {

    public static final String ISSUER = "taskflow";
    private static final Duration LIFETIME = Duration.ofMinutes(30);
    private final JwtEncoder encoder;

    JwtService(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public AuthResponse issue(long userId, String email) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(Long.toString(userId))
                .issuedAt(now)
                .expiresAt(now.plus(LIFETIME))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        var token = encoder.encode(JwtEncoderParameters.from(header, claims));
        return new AuthResponse(token.getTokenValue(), token.getExpiresAt(), email);
    }
}
