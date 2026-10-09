package com.group2.fse.transaction_service.interbank.outbound;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * I4. Internal only: the saga's service token, never a customer and never the gateway.
 */
@Slf4j
@RestController
@RequestMapping("/internal/v1/interbank")
@RequiredArgsConstructor
public class OutboundInterbankController {

    private final OutboundInterbankGateway gateway;

    @PostMapping("/outbound")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<OutboundInterbankResponse> send(@Valid @RequestBody OutboundInterbankRequest request) {
        log.info("I4: transaction {} for bank {}", request.getTransactionId(), request.getDestinationBankCode());
        return ResponseEntity.ok(gateway.send(request));
    }
}
