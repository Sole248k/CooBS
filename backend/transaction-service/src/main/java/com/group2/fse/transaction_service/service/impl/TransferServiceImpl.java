package com.group2.fse.transaction_service.service.impl;

import com.group2.fse.transaction_service.dto.TransferInitiateResponseDto;
import com.group2.fse.transaction_service.dto.TransferRequestDto;
import com.group2.fse.transaction_service.service.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Implementation of TransferService returning immediate 202 tracking response
 * with reference prefix TX-XXXXXX.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    @Override
    public TransferInitiateResponseDto initiateTransfer(TransferRequestDto request, String idempotencyKey) {
        String transactionId = UUID.randomUUID().toString();
        String datePart = DATE_FORMATTER.format(Instant.now());
        String randomHex = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String referenceNo = "TX-" + datePart + "-" + randomHex;

        log.info("Initiated transfer: transactionId={}, referenceNo={}, srcAccount={}, dstAccount={}, amount={}",
                transactionId, referenceNo, request.getSourceAccountId(), request.getDestinationAccountNumber(), request.getAmount());

        return TransferInitiateResponseDto.builder()
                .transactionId(transactionId)
                .referenceNo(referenceNo)
                .status("INITIATED")
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "PHP")
                .createdAt(Instant.now())
                .timelineUrl("/api/v1/transactions/" + transactionId + "/timeline")
                .build();
    }
}
