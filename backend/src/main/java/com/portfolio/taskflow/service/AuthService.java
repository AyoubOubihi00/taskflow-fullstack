package com.portfolio.taskflow.service;

import com.portfolio.taskflow.dto.AuthResponse;
import com.portfolio.taskflow.entity.User;
import com.portfolio.taskflow.repository.DemoTaskClaimRepository;
import com.portfolio.taskflow.repository.UserRepository;
import com.portfolio.taskflow.security.JwtService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final DemoTaskClaimRepository demoClaim;
    private final JwtService tokens;
    private final PasswordEncoder passwords;
    private final String claimCode;
    private final String dummyHash;

    AuthService(UserRepository users, DemoTaskClaimRepository demoClaim, JwtService tokens, PasswordEncoder passwords,
                @Value("${taskflow.demo.claim-code}") String claimCode) {
        this.users = users;
        this.demoClaim = demoClaim;
        this.tokens = tokens;
        this.passwords = passwords;
        this.claimCode = claimCode;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(String email, String password, String submittedCode) {
        if (users.existsByEmail(email)) {
            throw duplicateEmail();
        }
        boolean claimPending = demoClaim.isPending(email);
        if (claimPending && !validClaimCode(submittedCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Le code d’attribution est absent ou invalide.");
        }

        User user;
        try {
            user = users.saveAndFlush(new User(email, passwords.encode(password)));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateEmail();
        }
        if (claimPending) {
            demoClaim.assign(email, user.getId());
        }
        return tokens.issue(user.getId(), user.getEmail());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String email, String password) {
        var user = users.findByEmail(email);
        boolean passwordMatches = passwords.matches(password, user.map(User::passwordHash).orElse(dummyHash));
        if (user.isEmpty() || !passwordMatches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Adresse e-mail ou mot de passe incorrect.");
        }
        User account = user.orElseThrow();
        return tokens.issue(account.getId(), account.getEmail());
    }

    private boolean validClaimCode(String submittedCode) {
        return claimCode.length() >= 32 && submittedCode != null
                && MessageDigest.isEqual(claimCode.getBytes(StandardCharsets.UTF_8),
                submittedCode.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseStatusException duplicateEmail() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Cette adresse e-mail est déjà utilisée.");
    }
}
