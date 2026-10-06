package com.group2.fse.ledger_service.service;

import com.group2.fse.ledger_service.dto.*;

/**
 * Service managing two-phase commitment balance holds (L3 - L6).
 */
public interface HoldService {

    PlaceHoldResponseDto placeHold(PlaceHoldRequestDto request, String idempotencyKey);

    CaptureHoldResponseDto captureHold(Long holdId, CaptureHoldRequestDto request, String idempotencyKey);

    ReleaseHoldResponseDto releaseHold(Long holdId, ReleaseHoldRequestDto request, String idempotencyKey);

    HoldDetailDto getHold(Long holdId);
}
