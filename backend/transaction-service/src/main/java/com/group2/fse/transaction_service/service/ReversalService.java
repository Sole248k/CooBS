package com.group2.fse.transaction_service.service;

import com.group2.fse.transaction_service.dto.ReversalRequestDto;
import com.group2.fse.transaction_service.dto.ReversalResponseDto;

public interface ReversalService {
    ReversalResponseDto executeReversal(ReversalRequestDto request);
}
