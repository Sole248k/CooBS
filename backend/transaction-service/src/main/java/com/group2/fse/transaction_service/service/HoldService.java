package com.group2.fse.transaction_service.service;

import com.group2.fse.transaction_service.dto.HoldRequestDto;
import com.group2.fse.transaction_service.dto.HoldResponseDto;

public interface HoldService {
    HoldResponseDto placeHold(HoldRequestDto request);
    HoldResponseDto releaseHold(String holdId);
}
