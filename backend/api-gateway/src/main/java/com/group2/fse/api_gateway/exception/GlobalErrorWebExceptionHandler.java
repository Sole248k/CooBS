package com.group2.fse.api_gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group2.fse.api_gateway.filter.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standardized RFC-7807 Problem Details error handler for Spring Cloud Gateway.
 * Intercepts downstream connection failures, unrouted paths, and gateway exceptions,
 * producing unified JSON payloads with correlation ID tracking.
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = java.util.UUID.randomUUID().toString();
        }
        response.getHeaders().set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);

        HttpStatus status;
        String title;
        String detail;

        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            title = status.getReasonPhrase();
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                detail = "Rate limit exceeded. Too many requests. Please retry after some time.";
            } else {
                detail = rse.getReason() != null ? rse.getReason() : ex.getMessage();
            }
        } else if (ex instanceof ConnectException || (ex.getCause() != null && ex.getCause() instanceof ConnectException)) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            title = "Downstream Service Unavailable";
            detail = "The requested core banking service is currently offline or unreachable.";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            title = "Gateway Error";
            detail = "An unexpected error occurred while processing your request through the API Gateway.";
        }

        response.setStatusCode(status);

        Map<String, Object> errorAttributes = new LinkedHashMap<>();
        errorAttributes.put("type", "https://api.corebanking.group2.fse/errors/" + status.value());
        errorAttributes.put("title", title);
        errorAttributes.put("status", status.value());
        errorAttributes.put("detail", detail);
        errorAttributes.put("instance", exchange.getRequest().getURI().getRawPath());
        errorAttributes.put("timestamp", Instant.now().toString());
        errorAttributes.put("correlationId", correlationId);

        log.error("[GATEWAY-ERR] [{}] {} {} failed: {}",
                correlationId,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getRawPath(),
                detail,
                ex);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorAttributes);
        } catch (JsonProcessingException jpe) {
            bytes = "{\"title\":\"Internal Server Error\",\"status\":500}".getBytes();
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
