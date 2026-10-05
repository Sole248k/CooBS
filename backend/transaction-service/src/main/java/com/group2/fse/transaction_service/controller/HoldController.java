package com.group2.fse.transaction_service.controller;

import com.group2.fse.transaction_service.dto.HoldRequestDto;
import com.group2.fse.transaction_service.dto.HoldResponseDto;
import com.group2.fse.transaction_service.service.HoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/holds")
@RequiredArgsConstructor
public class HoldController {

    private final HoldService holdService;

    @PostMapping
    public ResponseEntity<HoldResponseDto> placeHold(@Valid @RequestBody HoldRequestDto request) {
        log.info("Received hold request for accountId={}", request.getAccountId());
        HoldResponseDto response = holdService.placeHold(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{holdId}/release")
    public ResponseEntity<HoldResponseDto> releaseHold(@PathVariable String holdId) {
        log.info("Received release request for holdId={}", holdId);
        HoldResponseDto response = holdService.releaseHold(holdId);
        return ResponseEntity.ok(response);
    }
}
