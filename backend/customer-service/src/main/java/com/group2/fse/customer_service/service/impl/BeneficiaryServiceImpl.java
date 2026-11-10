package com.group2.fse.customer_service.service.impl;

import com.group2.fse.customer_service.dto.BeneficiaryDto;
import com.group2.fse.customer_service.dto.CreateBeneficiaryRequest;
import com.group2.fse.customer_service.entity.Beneficiary;
import com.group2.fse.customer_service.exception.BeneficiaryNotFoundException;
import com.group2.fse.customer_service.repository.BeneficiaryRepository;
import com.group2.fse.customer_service.service.BeneficiaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BeneficiaryServiceImpl implements BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<BeneficiaryDto> getBeneficiaries(Long customerId) {
        return beneficiaryRepository.findByCustomerIdAndActiveTrue(customerId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public BeneficiaryDto addBeneficiary(Long customerId, CreateBeneficiaryRequest request) {
        var existing = beneficiaryRepository.findByCustomerIdAndActiveTrue(customerId).stream()
                .filter(b -> b.getAccountNumber().equals(request.getAccountNumber()))
                .findFirst();

        if (existing.isPresent()) {
            Beneficiary beneficiary = existing.get();
            beneficiary.setNickname(request.getNickname());
            if (request.getBankCode() != null) {
                beneficiary.setBankCode(request.getBankCode());
            }
            Beneficiary saved = beneficiaryRepository.save(beneficiary);
            log.info("Updated existing beneficiary ID {} for customer ID {}", saved.getBeneficiaryId(), customerId);
            return mapToDto(saved);
        }

        Beneficiary beneficiary = Beneficiary.builder()
                .customerId(customerId)
                .accountNumber(request.getAccountNumber())
                .nickname(request.getNickname())
                .bankCode(request.getBankCode() != null ? request.getBankCode() : "COOBS")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        Beneficiary saved = beneficiaryRepository.save(beneficiary);
        log.info("Saved beneficiary ID {} for customer ID {}", saved.getBeneficiaryId(), customerId);

        return mapToDto(saved);
    }

    @Override
    @Transactional
    public void deleteBeneficiary(Long customerId, Long beneficiaryId) {
        Beneficiary beneficiary = beneficiaryRepository.findByBeneficiaryIdAndCustomerId(beneficiaryId, customerId)
                .orElseThrow(() -> new BeneficiaryNotFoundException("Beneficiary not found with ID: " + beneficiaryId));

        beneficiary.setActive(false);
        beneficiaryRepository.save(beneficiary);
        log.info("Deactivated beneficiary ID {} for customer ID {}", beneficiaryId, customerId);
    }

    private BeneficiaryDto mapToDto(Beneficiary beneficiary) {
        return BeneficiaryDto.builder()
                .beneficiaryId(beneficiary.getBeneficiaryId())
                .customerId(beneficiary.getCustomerId())
                .nickname(beneficiary.getNickname())
                .accountNumber(beneficiary.getAccountNumber())
                .bankCode(beneficiary.getBankCode() != null ? beneficiary.getBankCode() : "COOBS")
                .active(beneficiary.getActive())
                .createdAt(beneficiary.getCreatedAt())
                .build();
    }
}
