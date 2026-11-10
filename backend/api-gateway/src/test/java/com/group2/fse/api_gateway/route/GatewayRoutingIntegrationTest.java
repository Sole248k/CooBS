package com.group2.fse.api_gateway.route;

import com.group2.fse.api_gateway.filter.CorrelationIdFilter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.health.redis.enabled=false",
        // What a deployment sets through CORS_ALLOWED_ORIGINS: where the hosted console is served from.
        "CORS_ALLOWED_ORIGINS=https://console.example.test,http://203.0.113.7:3000"})
class GatewayRoutingIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RouteDefinitionLocator routeDefinitions;
    @Autowired
    private org.springframework.cloud.gateway.config.HttpClientProperties httpClientProperties;

    private WebTestClient client;

    // Stands in for account-service and remembers the Origin header of the last call it received.
    private static final AtomicReference<String> originSeenByService = new AtomicReference<>("never called");
    private static final HttpServer accountService = startAccountService();

    private static HttpServer startAccountService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                originSeenByService.set(String.valueOf(exchange.getRequestHeaders().getFirst(HttpHeaders.ORIGIN)));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void accountServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("ACCOUNT_SERVICE_URL", () -> "http://localhost:" + accountService.getAddress().getPort());
    }

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
    @DisplayName("Actuator gateway routes endpoint should not be exposed on the public port")
    void shouldNotExposeGatewayRoutes() {
        client.get()
                .uri("/actuator/gateway/routes")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("Configured routes should be loaded")
    void shouldLoadConfiguredRoutes() {
        List<String> routeIds = routeDefinitions.getRouteDefinitions()
                .map(RouteDefinition::getId)
                .collectList()
                .block();

        assertThat(routeIds).contains(
                "auth-service-route",
                "account-service-accounts-route",
                "customer-service-route",
                "ledger-service-route",
                "notification-service-route",
                "audit-service-route",
                "transaction-service-route");
    }

    @Test
    @DisplayName("The orchestrator has no public route: it has no public endpoint")
    void shouldHaveNoRouteToTheOrchestrator() {
        List<RouteDefinition> routes = routeDefinitions.getRouteDefinitions().collectList().block();

        assertThat(routes).extracting(RouteDefinition::getId).doesNotContain("orchestration-service-route");
        assertThat(routes).allSatisfy(route -> assertThat(route.getUri().toString())
                .as("target of %s", route.getId())
                .doesNotContain(":8091"));
    }

    @Test
    @DisplayName("No route exists for an internal path")
    void shouldHaveNoRouteForAnInternalPath() {
        List<RouteDefinition> routes = routeDefinitions.getRouteDefinitions().collectList().block();

        assertThat(routes).extracting(RouteDefinition::getId).doesNotContain("risk-engine-route");
        assertThat(routes).allSatisfy(route -> assertThat(route.getPredicates().toString())
                .as("predicates of %s", route.getId())
                .doesNotContain("/internal"));
    }

    @Test
    @DisplayName("Calls to a target are bounded by a connect and a response timeout")
    void shouldBoundCallsToATarget() {
        assertThat(httpClientProperties.getConnectTimeout()).isEqualTo(3000);
        assertThat(httpClientProperties.getResponseTimeout()).isEqualTo(java.time.Duration.ofSeconds(15));
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
    @DisplayName("CORS: an origin named in CORS_ALLOWED_ORIGINS is allowed, any other is refused")
    void shouldTakeCorsOriginsFromTheEnvironment() {
        for (String hosted : List.of("https://console.example.test", "http://203.0.113.7:3000")) {
            client.options()
                    .uri("/api/v1/auth/staff/login")
                    .header(HttpHeaders.ORIGIN, hosted)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, hosted);
        }

        client.options()
                .uri("/api/v1/auth/staff/login")
                .header(HttpHeaders.ORIGIN, "https://elsewhere.example.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    @DisplayName("CORS: a call passed on by the console's nginx is same-origin, whatever the public address is")
    void shouldNotRefuseACallThatComesThroughTheConsoleProxy() {
        // In the cluster the browser loads the console from http://<public address> and nginx
        // passes /api on with that address as Host (frontend/nginx.conf). The address is not
        // known when CORS_ALLOWED_ORIGINS is set, so this must pass without being listed.
        client.post()
                .uri("/api/v1/auth/staff/login")
                .header(HttpHeaders.HOST, "203.0.113.9")
                .header(HttpHeaders.ORIGIN, "http://203.0.113.9")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(403));

        // The same call from a page served somewhere else is still refused.
        client.post()
                .uri("/api/v1/auth/staff/login")
                .header(HttpHeaders.HOST, "203.0.113.9")
                .header(HttpHeaders.ORIGIN, "http://198.51.100.7")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("CORS: the browser's origin is judged here and not passed on to the service")
    void shouldNotPassTheOriginOnToAService() {
        client.post()
                .uri("/api/v1/accounts/1/status")
                .header(HttpHeaders.ORIGIN, "https://console.example.test")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isNoContent()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://console.example.test");

        assertThat(originSeenByService.get()).isEqualTo("null");
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

    @Test
    @DisplayName("A percent-encoded internal path is rejected like a plain one")
    void shouldRejectAPercentEncodedInternalPath() {
        // %69 is "i". The router matches on the decoded path, so the perimeter filter must too.
        // Before this was checked, the request below reached the risk-engine route.
        client.post()
                .uri(java.net.URI.create("http://localhost:" + port + "/%69nternal/v1/risk/evaluate"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("A case-manipulated internal path is rejected across the perimeter")
    void shouldRejectCaseManipulatedInternalPath() {
        client.get()
                .uri("/Internal/v1/risk/evaluate")
                .exchange()
                .expectStatus().isForbidden();
    }
}

