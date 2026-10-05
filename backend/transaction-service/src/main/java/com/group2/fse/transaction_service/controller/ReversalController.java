package com.group2.fse.transaction_service.controller;

import com.group2.fse.transaction_service.dto.ReversalRequestDto;
import com.group2.fse.transaction_service.dto.ReversalResponseDto;
import com.group2.fse.transaction_service.service.ReversalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/reversals")
@RequiredArgsConstructor
public class ReversalController {

    private final ReversalService reversalService;

    @PostMapping
    public ResponseEntity<ReversalResponseDto> reverseTransaction(@Valid @RequestBody ReversalRequestDto request) {
        log.info("Received reversal request for originalRef={}", request.getOriginalReferenceNo());
        ReversalResponseDto response = reversalService.executeReversal(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
