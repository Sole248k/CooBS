package com.group2.fse.api_gateway.route;

import com.group2.fse.api_gateway.filter.CorrelationIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIntegrationTest {

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        this.client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    @DisplayName("Actuator health endpoint should return 200 OK with UP status")
    void shouldReturnHealthStatusUp() {
        client.get()
                .uri("/actuator/health")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("Actuator gateway routes endpoint should be accessible and list configured routes")
    void shouldListGatewayRoutes() {
        client.get()
                .uri("/actuator/gateway/routes")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[?(@.route_id == 'auth-service-route')]").exists()
                .jsonPath("$[?(@.route_id == 'account-service-accounts-route')]").exists()
                .jsonPath("$[?(@.route_id == 'ledger-service-route')]").exists()
                .jsonPath("$[?(@.route_id == 'notification-service-route')]").exists()
                .jsonPath("$[?(@.route_id == 'audit-service-route')]").exists();
    }

    @Test
    @DisplayName("Unmapped route should trigger GlobalErrorWebExceptionHandler returning RFC-7807 problem details")
    void shouldReturnProblemJsonForUnmappedRoute() {
        client.get()
                .uri("/api/v1/nonexistent-service/endpoint")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().value(CorrelationIdFilter.CORRELATION_ID_HEADER, org.junit.jupiter.api.Assertions::assertNotNull)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.title").isEqualTo("Not Found")
                .jsonPath("$.instance").isEqualTo("/api/v1/nonexistent-service/endpoint")
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.correlationId").isNotEmpty();
    }

    @Test
    @DisplayName("CORS preflight request from approved origin should return correct CORS headers")
    void shouldHandleCorsPreflightRequest() {
        client.options()
                .uri("/api/v1/accounts")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, Authorization, Idempotency-Key")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, methods ->
                        assertThat(methods).contains("POST"));
    }

    @Test
    @DisplayName("Internal route across perimeter should be rejected with 403 Forbidden")
    void shouldRejectInternalPerimeterRoute() {
        client.get()
                .uri("/internal/v1/risk/evaluate")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().value(CorrelationIdFilter.CORRELATION_ID_HEADER, org.junit.jupiter.api.Assertions::assertNotNull)
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.title").isEqualTo("Forbidden")
                .jsonPath("$.detail").isEqualTo("Access to internal microservice endpoints across the external API Gateway perimeter is strictly forbidden.")
                .jsonPath("$.instance").isEqualTo("/internal/v1/risk/evaluate");
    }
}

