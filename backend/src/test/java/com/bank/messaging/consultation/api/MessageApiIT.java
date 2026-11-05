package com.bank.messaging.consultation.api;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "bank.ingestion.auto-startup=false")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageApiIT {
    static final String DATABASE = "bank_api_" + UUID.randomUUID().toString().replace("-", "");
    static final Properties SETTINGS = settings();
    static String setting(String key, String fallback) {
        return System.getenv().getOrDefault(key, SETTINGS.getProperty(key, fallback));
    }
    static Properties settings() {
        var props = new Properties();
        try {
            var file = Path.of("../.env");
            if (Files.exists(file)) try (var reader = Files.newBufferedReader(file)) { props.load(reader); }
            return props;
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    static String url(String database) {
        return "jdbc:postgresql://" + setting("POSTGRES_HOST", "localhost") + ":" + setting("POSTGRES_PORT", "55432") + "/" + database;
    }
    static void admin(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(url(setting("POSTGRES_DB", "bank")),
                setting("POSTGRES_USER", "bank"), setting("POSTGRES_PASSWORD", ""));
             var statement = connection.createStatement()) { statement.execute(sql); }
    }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        admin("CREATE DATABASE " + DATABASE);
        registry.add("spring.datasource.url", () -> url(DATABASE));
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired HikariDataSource dataSource;
    @LocalServerPort int port;
    HttpClient client;

    @BeforeAll void client() { client = HttpClient.newHttpClient(); }
    @AfterAll void cleanup() throws Exception {
        client.close();
        dataSource.close();
        admin("DROP DATABASE " + DATABASE + " WITH (FORCE)");
    }
    @BeforeEach void clearFixture() { jdbc.update("delete from messaging.received_message"); }

    HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    long insert(String transportId, String payload) {
        return jdbc.queryForObject("""
            insert into messaging.received_message (source_queue,jms_message_id,correlation_id,content_type,payload_size,payload,received_at)
            values ('DEV.API.TEST', ?, 'correlation', 'text/plain;charset=UTF-8', ?, ?, '2026-11-04T12:00:00Z') returning id
            """, Long.class, transportId, payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, payload);
    }

    @Test void emptyListHasStablePaginationEnvelope() throws Exception {
        var response = get("/api/messages");
        assertThat(response.statusCode()).isEqualTo(200);
        var body = new JSONObject(response.body());
        assertThat(body.getJSONArray("content").length()).isZero();
        assertThat(body.getInt("page")).isZero();
        assertThat(body.getInt("size")).isEqualTo(20);
        assertThat(body.getLong("totalElements")).isZero();
        assertThat(body.getLong("totalPages")).isZero();
    }
    @Test void pagesAreOrderedWithoutLoadingPayloadAndBeyondLastIsEmpty() throws Exception {
        long first = insert("first", "secret-first");
        long second = insert("second", "secret-second");
        long third = insert("third", "secret-third");
        var response = get("/api/messages?page=0&size=2");
        assertThat(response.statusCode()).isEqualTo(200);
        var body = new JSONObject(response.body());
        assertThat(body.getLong("totalElements")).isEqualTo(3);
        assertThat(body.getInt("totalPages")).isEqualTo(2);
        assertThat(body.getJSONArray("content").getJSONObject(0).getLong("id")).isEqualTo(third);
        assertThat(body.getJSONArray("content").getJSONObject(1).getLong("id")).isEqualTo(second);
        assertThat(response.body()).doesNotContain("secret-", "\"payload\":");
        var next = new JSONObject(get("/api/messages?page=1&size=2").body());
        assertThat(next.getJSONArray("content").getJSONObject(0).getLong("id")).isEqualTo(first);
        var beyond = new JSONObject(get("/api/messages?page=2&size=2").body());
        assertThat(beyond.getJSONArray("content").length()).isZero();
        assertThat(get("/api/messages?size=100").statusCode()).isEqualTo(200);
    }
    @Test void datePrecedesIdInTheSortOrder() throws Exception {
        long olderId = insert("newer-date", "test");
        insert("older-date", "test");
        jdbc.update("update messaging.received_message set received_at = '2026-11-05T12:00:00Z' where id = ?", olderId);
        var body = new JSONObject(get("/api/messages").body());
        assertThat(body.getJSONArray("content").getJSONObject(0).getLong("id")).isEqualTo(olderId);
    }
    @Test void detailContainsRawUnicodeTextAndMetadata() throws Exception {
        String payload = "<script>test</script> Paiement €\nligne";
        long id = insert("ID:detail", payload);
        var response = get("/api/messages/" + id);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElse("")).contains("application/json");
        var body = new JSONObject(response.body());
        assertThat(body.getLong("id")).isEqualTo(id);
        assertThat(body.getString("payload")).isEqualTo(payload);
        assertThat(body.getString("sourceQueue")).isEqualTo("DEV.API.TEST");
        assertThat(body.getString("jmsMessageId")).isEqualTo("ID:detail");
        assertThat(body.getString("correlationId")).isEqualTo("correlation");
        assertThat(body.getString("receivedAt")).isEqualTo("2026-11-04T12:00:00Z");
        assertThat(body.getInt("payloadSize")).isEqualTo(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"?page=-1", "?size=0", "?size=101", "?page=x", "?size=", "?page=", "?size=%20", "?page=10001", "?page=999999999999999999", "/abc", "/0", "/-1"})
    void invalidParametersReturnProblemDetails(String suffix) throws Exception {
        assertProblem(get("/api/messages" + suffix), 400);
    }
    @Test void unknownMessageReturns404() throws Exception {
        assertProblem(get("/api/messages/9223372036854775807"), 404);
    }
    @Test void databaseFailureDoesNotExposeSqlOrPayload() throws Exception {
        jdbc.execute("alter table messaging.received_message rename to unavailable_message");
        try {
            var response = get("/api/messages");
            assertProblem(response, 503);
            assertThat(response.body()).doesNotContain("SELECT", "received_message", "jdbc", "password");
        } finally { jdbc.execute("alter table messaging.unavailable_message rename to received_message"); }
    }
    @Test void filtersApplyToBothRowsAndTotalsAndSortRemainsStable() throws Exception {
        long first = insert("ID:target", "private-first");
        long second = insert("ID:second", "private-second");
        insert("ID:outside", "private-outside");
        jdbc.update("update messaging.received_message set received_at = '2026-11-06T12:00:00Z' where jms_message_id = 'ID:outside'");
        var response = get("/api/messages?sourceQueue=DEV.API.TEST&from=2026-11-04T12:00:00Z&to=2026-11-05T00:00:00Z&sort=receivedAt,asc&size=1");
        assertThat(response.statusCode()).isEqualTo(200);
        var body = new JSONObject(response.body());
        assertThat(body.getLong("totalElements")).isEqualTo(2);
        assertThat(body.getJSONArray("content").getJSONObject(0).getLong("id")).isEqualTo(first);
        assertThat(response.body()).doesNotContain("private-", "\"payload\":");
        var next = new JSONObject(get("/api/messages?sort=receivedAt,asc&page=1&size=1").body());
        assertThat(next.getJSONArray("content").getJSONObject(0).getLong("id")).isEqualTo(second);
        var exact = new JSONObject(get("/api/messages?messageId=ID:target&sourceQueue=DEV.API.TEST").body());
        assertThat(exact.getLong("totalElements")).isEqualTo(1);
        assertThat(exact.getJSONArray("content").getJSONObject(0).getLong("id")).isEqualTo(first);
        assertThat(new JSONObject(get("/api/messages?messageId=target").body()).getLong("totalElements")).isZero();
        assertThat(new JSONObject(get("/api/messages?sourceQueue=OTHER").body()).getLong("totalElements")).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "?from=invalid", "?to=", "?from=2026-11-05T00:00:00Z&to=2026-11-04T00:00:00Z",
        "?from=2026-11-04T00:00:00", "?sort=id,desc", "?sort=receivedAt,invalid", "?sort=",
        "?messageId=", "?sourceQueue=%20"
    })
    void invalidSearchReturnsProblemDetails(String suffix) throws Exception {
        assertProblem(get("/api/messages" + suffix), 400);
    }

    @Test void openApiContractIsServedWithTheConsultationOperations() throws Exception {
        var response = get("/openapi.json");
        assertThat(response.statusCode()).isEqualTo(200);
        var contract = new JSONObject(response.body());
        assertThat(contract.getString("openapi")).isEqualTo("3.1.0");
        assertThat(contract.getJSONObject("paths").keySet()).containsExactlyInAnyOrder("/api/messages", "/api/messages/{id}");
        assertThat(contract.getJSONObject("paths").getJSONObject("/api/messages").getJSONObject("get")
                .getJSONArray("parameters").length()).isEqualTo(7);
    }

    void assertProblem(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("content-type").orElse("")).contains("application/problem+json");
        var body = new JSONObject(response.body());
        assertThat(body.getInt("status")).isEqualTo(status);
        assertThat(body.getString("title")).isNotBlank();
        assertThat(body.getString("detail")).isNotBlank();
        assertThat(body.getString("instance")).startsWith("/api/messages");
    }
}
