package com.group2.fse.orchestration_service.client;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import javax.crypto.SecretKey;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import tools.jackson.databind.json.JsonMapper;

/**
 * What every /internal/v1 call from this service shares: a timeout, the service token
 * (contract section 2.3), X-Correlation-ID, and one way of reporting failure.
 */
@Component
public class InternalApi {

    public static final String CORRELATION_HEADER = "X-Correlation-ID";
    public static final String CORRELATION_MDC_KEY = "correlationId";
    /** A concurrent ledger operation on the same account: nothing was refused, try again. */
    private static final String RETRYABLE_CONFLICT = "CONCURRENT_TRANSACTION_IN_FLIGHT";
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(5);

    private final SecretKey key;
    private final String serviceName;
    private final JsonMapper jsonMapper;

    public InternalApi(@Value("${app.security.jwt.secret}") String jwtSecret,
            @Value("${spring.application.name}") String serviceName,
            JsonMapper jsonMapper) {
        this.key = signingKey(jwtSecret);
        this.serviceName = serviceName;
        this.jsonMapper = jsonMapper;
    }

    public RestClient client(String baseUrl, Duration timeout) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(serviceToken());
                    String correlationId = MDC.get(CORRELATION_MDC_KEY);
                    if (correlationId != null) {
                        request.getHeaders().set(CORRELATION_HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    String errorCode = null;
                    if (status.is4xxClientError()) {
                        try {
                            errorCode = jsonMapper.readTree(response.getBody()).path("errorCode").asString(null);
                        } catch (RuntimeException unreadable) {
                            // falls through to the generic code below
                        }
                        if (errorCode == null) {
                            errorCode = "INTERNAL_SERVER_ERROR";
                        } else if (RETRYABLE_CONFLICT.equals(errorCode)) {
                            errorCode = null;
                        }
                    }
                    throw new DownstreamException(errorCode, request.getURI() + " answered " + status.value(), null);
                })
                .build();
    }

    /** Runs a call so that every failure, including a timeout or an empty body, is a DownstreamException. */
    public <T> T call(Supplier<T> call) {
        T result;
        try {
            result = call.get();
        } catch (RestClientException e) {
            throw new DownstreamException(null, e.getMessage(), e);
        }
        if (result == null) {
            throw new DownstreamException(null, "empty response body", null);
        }
        return result;
    }

    private String serviceToken() {
        return Jwts.builder()
                .subject(serviceName)
                .claim("userId", 0)
                .claim("roles", List.of("ROLE_SERVICE"))
                .claim("userType", "SERVICE")
                .expiration(Date.from(Instant.now().plus(TOKEN_LIFETIME)))
                .signWith(key)
                .compact();
    }

    /** Must derive the same key as JwtTokenProvider.init, which every service uses to verify. */
    private static SecretKey signingKey(String jwtSecret) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (Exception e) {
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
