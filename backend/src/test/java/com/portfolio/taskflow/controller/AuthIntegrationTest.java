package com.portfolio.taskflow.controller;

import com.portfolio.taskflow.repository.DemoTaskClaimRepository;
import com.portfolio.taskflow.repository.UserRepository;
import com.portfolio.taskflow.security.JwtService;
import com.portfolio.taskflow.support.TestSecrets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private static final String TEST_DB = "jdbc:h2:mem:taskflow_security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    private static final String REGISTER = "/api/auth/register";
    private static final String LOGIN = "/api/auth/login";
    private static final String TASKS = "/api/tasks";
    private static final String DEMO_EMAIL = "user01@mail.com";
    private static final String CLAIM_CODE = TestSecrets.randomKey();
    private static final String PASSWORD = "Test password with 24 chars";

    @DynamicPropertySource
    static void databaseAndSecrets(DynamicPropertyRegistry registry) throws SQLException {
        try (var connection = DriverManager.getConnection(TEST_DB, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__task_item.sql"));
            try (var insert = connection.prepareStatement(
                    "INSERT INTO task_item (id, title, description, status) VALUES (?, ?, ?, 'TODO')")) {
                for (long id = 2; id <= 8; id++) {
                    insert.setLong(1, id);
                    insert.setString(2, "Existing task " + id);
                    insert.setString(3, "Description " + id);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            try (var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE task_item ALTER COLUMN id RESTART WITH 9");
            }
        }
        registry.add("spring.datasource.url", () -> TEST_DB);
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("taskflow.jwt.key", TestSecrets::randomKey);
        registry.add("taskflow.demo.claim-code", () -> CLAIM_CODE);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private JwtEncoder encoder;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DemoTaskClaimRepository demoClaim;
    @Autowired
    private TransactionTemplate transactions;

    @Test
    void registersWithBCryptAndLogsInWithNormalizedEmail() throws Exception {
        JsonNode registration = body(submit(REGISTER, Map.of("email", "  Auth@EXAMPLE.COM  ", "password", PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.email").value("auth@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.expiresAt").isString()));

        var user = users.findByEmail("auth@example.com").orElseThrow();
        assertThat(user.passwordHash()).hasSize(60).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, user.passwordHash())).isTrue();
        authenticated(get(TASKS), registration.path("accessToken").asText())
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        submit(LOGIN, Map.of("email", "AUTH@example.com", "password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        submit(REGISTER, Map.of("email", "auth@example.com", "password", PASSWORD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void rejectsInvalidInputsAndPasswordsOverBCryptByteLimit() throws Exception {
        submit(REGISTER, Map.of("email", "invalid-email", "password", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email[0]").isNotEmpty())
                .andExpect(jsonPath("$.errors.password[0]").isNotEmpty());

        submit(REGISTER, Map.of("email", "long@example.com", "password", "é".repeat(37)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password[0]").value("Le mot de passe est limité à 72 octets UTF-8."));
        assertThat(users.findByEmail("long@example.com")).isEmpty();
    }

    @Test
    void reportsWrongPasswordAndUnknownAccountIdentically() throws Exception {
        register("credentials@example.com");
        JsonNode wrong = body(submit(LOGIN, Map.of("email", "credentials@example.com", "password", "incorrect"))
                .andExpect(status().isUnauthorized()));
        JsonNode unknown = body(submit(LOGIN, Map.of("email", "unknown@example.com", "password", "incorrect"))
                .andExpect(status().isUnauthorized()));
        assertThat(wrong).isEqualTo(unknown);
    }

    static Stream<MockHttpServletRequestBuilder> protectedRequests() {
        return Stream.of(get(TASKS), get(TASKS + "/2"), post(TASKS), put(TASKS + "/2"), delete(TASKS + "/2"));
    }

    @ParameterizedTest
    @MethodSource("protectedRequests")
    void requiresBearerOnEveryTaskRoute(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @Test
    void rejectsMalformedExpiredAndWrongIssuerJwt() throws Exception {
        Instant now = Instant.now();
        for (String token : List.of("invalid", token(JwtService.ISSUER, now.minusSeconds(1)),
                token("other-issuer", now.plusSeconds(600)))) {
            authenticated(get(TASKS), token).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }
    }

    @Test
    void handlesAngularPreflightAndReturnsJsonForbidden() throws Exception {
        mvc.perform(options(TASKS + "/2")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));

        authenticated(get("/api/private"), register("forbidden@example.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void isolatesTasksAndIgnoresOwnerSuppliedByClient() throws Exception {
        String owner = register("owner@example.com");
        String other = register("other@example.com");
        long otherId = users.findByEmail("other@example.com").orElseThrow().getId();
        Map<String, Object> payload = Map.of("title", "Private task", "status", "TODO", "ownerId", otherId);
        long id = body(authenticated(json(post(TASKS), payload), owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").doesNotExist())).path("id").asLong();

        authenticated(get(TASKS), owner).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        authenticated(get(TASKS), other).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        rejectTaskAccess(other, id);

        authenticated(json(put(TASKS + "/" + id), Map.of("title", "Updated title", "status", "DONE")), owner)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
        authenticated(get(TASKS + "/" + id), owner)
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Updated title"));
        authenticated(delete(TASKS + "/" + id), owner).andExpect(status().isNoContent());
        authenticated(get(TASKS + "/" + id), owner).andExpect(status().isNotFound());
    }

    @Test
    void preservesLegacyRowsAndAssignsOnlyTheSixAuthorizedTasksOnce() throws Exception {
        List<Map<String, Object>> initial = legacyRows();
        assertThat(initial).hasSize(7);
        String outsider = tokenFrom(submit(REGISTER, Map.of(
                "email", "another@example.com", "password", PASSWORD, "claimCode", CLAIM_CODE)));
        authenticated(get(TASKS), outsider).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        rejectTaskAccess(outsider, 2L);
        rejectTaskAccess(outsider, 8L);

        submit(REGISTER, Map.of("email", DEMO_EMAIL, "password", PASSWORD))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        submit(REGISTER, Map.of("email", DEMO_EMAIL, "password", PASSWORD, "claimCode", "incorrect"))
                .andExpect(status().isForbidden());
        assertThat(users.findByEmail(DEMO_EMAIL)).isEmpty();

        String owner = tokenFrom(submit(REGISTER, Map.of(
                "email", DEMO_EMAIL, "password", PASSWORD, "claimCode", CLAIM_CODE)));
        authenticated(get(TASKS), owner).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(2, 3, 4, 5, 6, 7)));
        rejectTaskAccess(owner, 8L);
        rejectTaskAccess(outsider, 2L);
        assertThat(legacyRows()).isEqualTo(initial);
        assertThat(jdbc.sql("SELECT owner_id FROM task_item WHERE id = 8").query(Long.class).optional()).isEmpty();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM task_demo_claim WHERE claimed_at IS NOT NULL")
                .query(Integer.class).single()).isEqualTo(6);

        String signedIn = body(submit(LOGIN, Map.of("email", DEMO_EMAIL, "password", PASSWORD))
                .andExpect(status().isOk())).path("accessToken").asText();
        authenticated(get(TASKS), signedIn).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(2, 3, 4, 5, 6, 7)));
        submit(REGISTER, Map.of("email", DEMO_EMAIL, "password", PASSWORD))
                .andExpect(status().isConflict());

        long outsiderId = users.findByEmail("another@example.com").orElseThrow().getId();
        transactions.executeWithoutResult(transaction -> {
            jdbc.sql("UPDATE task_item SET owner_id = NULL WHERE id = 2").update();
            demoClaim.assign(DEMO_EMAIL, outsiderId);
            assertThat(jdbc.sql("SELECT owner_id FROM task_item WHERE id = 2").query(Long.class).optional()).isEmpty();
            transaction.setRollbackOnly();
        });
    }

    private List<Map<String, Object>> legacyRows() {
        return jdbc.sql("SELECT id, title, description, status FROM task_item WHERE id BETWEEN 2 AND 8 ORDER BY id")
                .query().listOfRows();
    }

    private void rejectTaskAccess(String token, long id) throws Exception {
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE)) {
            authenticated(json(request(method, TASKS + "/" + id), Map.of("title", "Another title")), token)
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        }
    }

    private String register(String email) throws Exception {
        return tokenFrom(submit(REGISTER, Map.of("email", email, "password", PASSWORD)));
    }

    private String tokenFrom(ResultActions result) throws Exception {
        return body(result.andExpect(status().isCreated()).andExpect(jsonPath("$.accessToken").isNotEmpty()))
                .path("accessToken").asText();
    }

    private String token(String issuer, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject("1").expiresAt(expiresAt).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private ResultActions authenticated(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions submit(String path, Map<String, ?> payload) throws Exception {
        return mvc.perform(json(post(path), payload));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Map<String, ?> payload) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }
}
