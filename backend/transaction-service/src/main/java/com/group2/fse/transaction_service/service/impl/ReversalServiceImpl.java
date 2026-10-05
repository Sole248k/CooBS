package com.group2.fse.transaction_service.service.impl;

import com.group2.fse.transaction_service.dto.ReversalRequestDto;
import com.group2.fse.transaction_service.dto.ReversalResponseDto;
import com.group2.fse.transaction_service.service.ReversalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReversalServiceImpl implements ReversalService {

    @Override
    public ReversalResponseDto executeReversal(ReversalRequestDto request) {
        String reversalId = "REV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Executing transaction reversal: reversalId={}, originalRef={}, reason={}",
                reversalId, request.getOriginalReferenceNo(), request.getReason());

        return ReversalResponseDto.builder()
                .reversalId(reversalId)
                .originalReferenceNo(request.getOriginalReferenceNo())
                .amount(request.getAmount())
                .status("REVERSED")
                .timestamp(Instant.now())
                .build();
    }
}
