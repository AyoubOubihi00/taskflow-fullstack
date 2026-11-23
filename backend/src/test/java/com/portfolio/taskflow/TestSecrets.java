package com.portfolio.taskflow;

import java.security.SecureRandom;
import java.util.Base64;

final class TestSecrets {

    private TestSecrets() {
    }

    static String randomKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
