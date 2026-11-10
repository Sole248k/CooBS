package com.group2.fse.customer_service.service;

import com.group2.fse.customer_service.dto.*;
import org.springframework.data.domain.Pageable;

public interface CustomerService {
    CustomerProfileResponse getCustomerProfile(Long customerId);
    CustomerProfileResponse getCustomerById(Long customerId);
    RiskProfileResponse getRiskProfile(Long customerId);

    KycRequestResponse submitKyc(Long customerId, KycSubmitRequest request);
    KycRequestResponse submitKycUpdateRequest(Long customerId, KycUpdateRequestDto request);
    PageResponse<KycRequestResponse> getPendingKycUpdateRequests(String status, Pageable pageable);
    KycRequestResponse approveKycUpdateRequest(Long requestId, Long staffUserId);
    KycRequestResponse rejectKycUpdateRequest(Long requestId, Long staffUserId, String rejectionReason);
}
