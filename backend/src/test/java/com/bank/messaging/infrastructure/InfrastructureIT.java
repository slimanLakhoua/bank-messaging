package com.bank.messaging.infrastructure;

import jakarta.jms.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "bank.ingestion.auto-startup=false")
class InfrastructureIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired ConnectionFactory connectionFactory;
    @LocalServerPort int port;

    @Test
    void migrationCreatesTheApplicationSchema() {
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.schemata where schema_name = 'messaging'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void applicationIsHealthyWithRealDatabaseAndMq() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/actuator/health"))
                    .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"UP\"");
        }
    }

    @Test
    void applicationCredentialsCanBrowseConfiguredQueues() {
        try (var context = connectionFactory.createContext()) {
            for (String name : new String[]{"DEV.BANK.IN", "DEV.BANK.BACKOUT"}) {
                try (var browser = context.createBrowser(context.createQueue(name))) {
                    assertThat(browser.getEnumeration()).isNotNull();
                } catch (jakarta.jms.JMSException ex) {
                    throw new AssertionError(ex);
                }
            }
        }
    }
}
