package com.group2.fse.api_gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Enforces Capstone Pillar 3 Network Perimeter Isolation:
 * Rejects any external ingress requests targeting internal-only service routes
 * (e.g. /internal/**, /internal/v1/risk/**) with HTTP 403 Forbidden.
 */
@Slf4j
@Component
public class InternalPerimeterRejectionFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String rawPath = exchange.getRequest().getURI().getRawPath();
        String normalizedPath = exchange.getRequest().getPath().pathWithinApplication().value();

        if (isInternalPath(rawPath) || isInternalPath(normalizedPath) || isInternalPath(decoded(rawPath))) {
            String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
            log.warn("[PERIMETER-REJECTION] [{}] Blocked external access to internal endpoint: raw={}, normalized={}",
                    correlationId, rawPath, normalizedPath);
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access to internal microservice endpoints across the external API Gateway perimeter is strictly forbidden."
            ));
        }
        return chain.filter(exchange);
    }

    private static boolean isInternalPath(String p) {
        if (p == null) return false;
        String lower = p.toLowerCase();
        return lower.equals("/internal") || lower.startsWith("/internal/");
    }

    /** A path whose percent-encoding cannot be decoded matches no route, so it is left as it is. */
    private static String decoded(String rawPath) {
        if (rawPath == null) return "";
        try {
            return UriUtils.decode(rawPath, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            return rawPath;
        }
    }

    @Override
    public int getOrder() {
        // Runs at very high precedence in WebFlux filter chain before routing
        return -1;
    }
}

