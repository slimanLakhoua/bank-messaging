package com.bank.messaging.infrastructure;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"bank.ingestion.auto-startup=false", "spring.application.name=readiness-test"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReadinessIT {
    @Autowired HikariDataSource dataSource;
    @LocalServerPort int port;

    @Test void dependencyFailureChangesReadinessButLeavesLivenessUp() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            assertThat(get(client, "/actuator/health/readiness").statusCode()).isEqualTo(200);
            assertThat(get(client, "/actuator/metrics/jvm.memory.used").statusCode()).isEqualTo(200);
            dataSource.close();
            assertThat(get(client, "/actuator/health/readiness").statusCode()).isEqualTo(503);
            assertThat(get(client, "/actuator/health/liveness").statusCode()).isEqualTo(200);
        }
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
