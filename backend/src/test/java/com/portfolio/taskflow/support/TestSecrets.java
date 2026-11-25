package com.portfolio.taskflow.support;

import java.security.SecureRandom;
import java.util.Base64;

public final class TestSecrets {

    private TestSecrets() {
    }

    public static String randomKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
