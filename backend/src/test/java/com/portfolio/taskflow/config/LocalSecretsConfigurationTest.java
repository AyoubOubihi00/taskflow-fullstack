package com.portfolio.taskflow.config;

import com.portfolio.taskflow.support.TestSecrets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalSecretsConfigurationTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void loadsTheSameLocalCredentialsAcrossRestartsWithoutAClaimCode() throws Exception {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("local secrets"));
        Path file = directory.resolve(".env.local");
        String key = TestSecrets.randomKey();
        String databasePassword = TestSecrets.randomKey();
        Files.writeString(file, "TASKFLOW_JWT_KEY=" + key + "\nDATABASE_PASSWORD=" + databasePassword + "\n");

        for (int start = 0; start < 2; start++) {
            var environment = new StandardEnvironment();
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().addFirst(new MapPropertySource("testSecretsLocation", Map.of(
                    "spring.config.location", "classpath:/application.properties",
                    "TASKFLOW_LOCAL_SECRETS", file.toString().replace('\\', '/')
            )));

            ConfigDataEnvironmentPostProcessor.applyTo(environment);

            assertThat(key.equals(environment.getProperty("taskflow.jwt.key")))
                    .as("Spring uses the signing key from the same local file").isTrue();
            assertThat(databasePassword.equals(environment.getProperty("spring.datasource.password")))
                    .as("Spring uses DATABASE_PASSWORD from the local secrets file").isTrue();
            assertThat(environment.getProperty("taskflow.demo.claim-code")).isEmpty();
        }
    }
}
