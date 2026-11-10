package com.group2.fse.api_gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ConnectTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalErrorWebExceptionHandler: what a caller sees when a target does not answer")
class GlobalErrorWebExceptionHandlerTest {

    private final GlobalErrorWebExceptionHandler handler = new GlobalErrorWebExceptionHandler(new ObjectMapper());

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/accounts/my-accounts").build());
    }

    @Test
    @DisplayName("A target that cannot be reached within the connect timeout is 503")
    void connectTimeoutIsServiceUnavailable() {
        MockServerWebExchange exchange = exchange();

        // What the HTTP client raises when the on-premises link drops the connection attempt.
        handler.handle(exchange, new ConnectTimeoutException("connection timed out after 3000 ms")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"status\":503")
                .contains("\"instance\":\"/api/v1/accounts/my-accounts\"");
    }

    @Test
    @DisplayName("A target that accepts the call but does not answer within the response timeout is 504")
    void responseTimeoutIsGatewayTimeout() {
        MockServerWebExchange exchange = exchange();

        // The routing filter turns its response timeout into this exception.
        handler.handle(exchange, new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Response took longer than timeout: PT15S")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"status\":504");
    }
}
