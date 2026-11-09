package com.peergeek.gateway;

import com.peergeek.gateway.filter.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "MONOLITH_BASE_URL=http://localhost:59999",
        "NOTIFICATION_SERVICE_URL=http://localhost:59998"
})
@AutoConfigureWebTestClient
class ApiGatewayApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private CorrelationIdFilter correlationIdFilter;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void contextLoads() {
        assertNotNull(applicationContext, "ApplicationContext should not be null");
        assertNotNull(routeLocator, "RouteLocator should be present in context");
        assertNotNull(correlationIdFilter, "CorrelationIdFilter should be present in context");
    }

    @Test
    void actuatorHealthReturnsUp() {
        webTestClient.get()
                .uri("/actuator/health")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void correlationIdGeneratedWhenMissing() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(CorrelationIdFilter.CORRELATION_ID_HEADER, value -> {
                    assertNotNull(value);
                    assertTrue(value.length() > 10, "Correlation ID should be a valid UUID string");
                });
    }

    @Test
    void correlationIdPreservedWhenSupplied() {
        String testCorrelationId = "test-corr-id-12345";
        webTestClient.get()
                .uri("/actuator/health")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, testCorrelationId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(CorrelationIdFilter.CORRELATION_ID_HEADER, testCorrelationId);
    }

    @Test
    void downstreamUnreachableReturnsServiceUnavailableJson() {
        // Downstream monolith is not running on test random port, so calling /api/v1/health should trigger GatewayExceptionHandler
        webTestClient.get()
                .uri("/api/v1/health")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectHeader().exists(CorrelationIdFilter.CORRELATION_ID_HEADER)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.error").isEqualTo("Service Unavailable")
                .jsonPath("$.path").isEqualTo("/api/v1/health")
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.timestamp").isNotEmpty();
    }

    @Test
    void notificationRouteUnreachableReturnsServiceUnavailableJson() {
        webTestClient.get()
                .uri("/api/v1/notifications")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectHeader().exists(CorrelationIdFilter.CORRELATION_ID_HEADER)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.error").isEqualTo("Service Unavailable")
                .jsonPath("$.path").isEqualTo("/api/v1/notifications");
    }
}
