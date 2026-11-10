package com.group2.fse.order_service.client;

import com.group2.fse.order_service.client.dto.*;
import com.group2.fse.order_service.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class LedgerHoldClient {

    private final RestTemplate restTemplate;
    private final String ledgerServiceBaseUrl;
    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    public LedgerHoldClient(
            RestTemplate restTemplate,
            @Value("${app.clients.ledger.base-url:http://localhost:8083}") String ledgerServiceBaseUrl,
            JwtTokenProvider jwtTokenProvider) {
        this.restTemplate = restTemplate;
        this.ledgerServiceBaseUrl = ledgerServiceBaseUrl;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public PlaceHoldResponseDto placeHold(PlaceHoldRequestDto request, String idempotencyKey) {
        String url = ledgerServiceBaseUrl + "/internal/v1/ledger/holds";
        log.info("Calling ledger-service to place hold: accountId={}, amount={}, ref={}",
                request.getAccountId(), request.getAmount(), request.getReferenceNo());

        HttpHeaders headers = createHeaders(idempotencyKey);
        HttpEntity<PlaceHoldRequestDto> entity = new HttpEntity<>(request, headers);

        ResponseEntity<PlaceHoldResponseDto> response = restTemplate.postForEntity(url, entity, PlaceHoldResponseDto.class);
        return response.getBody();
    }

    public CaptureHoldResponseDto captureHold(Long holdId, CaptureHoldRequestDto request, String idempotencyKey) {
        String url = ledgerServiceBaseUrl + "/internal/v1/ledger/holds/" + holdId + "/capture";
        log.info("Calling ledger-service to capture hold {}: dstAccount={}", holdId, request.getDestinationAccountId());

        HttpHeaders headers = createHeaders(idempotencyKey);
        HttpEntity<CaptureHoldRequestDto> entity = new HttpEntity<>(request, headers);

        ResponseEntity<CaptureHoldResponseDto> response = restTemplate.postForEntity(url, entity, CaptureHoldResponseDto.class);
        return response.getBody();
    }

    public ReleaseHoldResponseDto releaseHold(Long holdId, ReleaseHoldRequestDto request, String idempotencyKey) {
        String url = ledgerServiceBaseUrl + "/internal/v1/ledger/holds/" + holdId + "/release";
        log.info("Calling ledger-service to release hold {}: reason={}", holdId, request != null ? request.getReason() : null);

        HttpHeaders headers = createHeaders(idempotencyKey);
        HttpEntity<ReleaseHoldRequestDto> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ReleaseHoldResponseDto> response = restTemplate.postForEntity(url, entity, ReleaseHoldResponseDto.class);
        return response.getBody();
    }

    private HttpHeaders createHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwtTokenProvider.generateServiceToken());
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }
}
