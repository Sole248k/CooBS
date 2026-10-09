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
        String path = exchange.getRequest().getURI().getRawPath();
        // Routes are matched on the decoded path, so the decoded form is checked as well:
        // /%69nternal/... is /internal/... to the router.
        if (path != null && (path.startsWith("/internal/") || decoded(path).startsWith("/internal/"))) {
            String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
            log.warn("[PERIMETER-REJECTION] [{}] Blocked external access to internal endpoint: {}", correlationId, path);
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access to internal microservice endpoints across the external API Gateway perimeter is strictly forbidden."
            ));
        }
        return chain.filter(exchange);
    }

    /** A path whose percent-encoding cannot be decoded matches no route, so it is left as it is. */
    private static String decoded(String rawPath) {
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

