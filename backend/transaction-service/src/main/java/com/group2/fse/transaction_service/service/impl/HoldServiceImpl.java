package com.group2.fse.transaction_service.service.impl;

import com.group2.fse.transaction_service.dto.HoldRequestDto;
import com.group2.fse.transaction_service.dto.HoldResponseDto;
import com.group2.fse.transaction_service.service.HoldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldServiceImpl implements HoldService {

    @Override
    public HoldResponseDto placeHold(HoldRequestDto request) {
        String holdId = "HLD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Placing balance hold: holdId={}, accountId={}, amount={}, reason={}",
                holdId, request.getAccountId(), request.getAmount(), request.getReason());

        return HoldResponseDto.builder()
                .holdId(holdId)
                .accountId(request.getAccountId())
                .amount(request.getAmount())
                .reason(request.getReason())
                .status("HELD")
                .createdAt(Instant.now())
                .build();
    }

    @Override
    public HoldResponseDto releaseHold(String holdId) {
        log.info("Releasing balance hold: holdId={}", holdId);

        return HoldResponseDto.builder()
                .holdId(holdId)
                .status("RELEASED")
                .createdAt(Instant.now())
                .build();
    }
}
