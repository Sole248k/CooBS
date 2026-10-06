package com.group2.fse.customer_service.service;

import com.group2.fse.customer_service.dto.BeneficiaryDto;
import com.group2.fse.customer_service.dto.CreateBeneficiaryRequest;

import java.util.List;

public interface BeneficiaryService {
    List<BeneficiaryDto> getBeneficiaries(Long customerId);
    BeneficiaryDto addBeneficiary(Long customerId, CreateBeneficiaryRequest request);
    void deleteBeneficiary(Long customerId, Long beneficiaryId);
}
