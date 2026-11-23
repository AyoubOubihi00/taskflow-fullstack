package com.portfolio.taskflow;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
class JwtService {

    static final String ISSUER = "taskflow";
    private static final Duration LIFETIME = Duration.ofMinutes(30);
    private final JwtEncoder encoder;

    JwtService(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    AuthResponse issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(LIFETIME))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        var token = encoder.encode(JwtEncoderParameters.from(header, claims));
        return new AuthResponse(token.getTokenValue(), token.getExpiresAt(), user.getEmail());
    }

    record AuthResponse(String accessToken, Instant expiresAt, String email) {
        @Override
        public String toString() {
            return "AuthResponse";
        }
    }
}
