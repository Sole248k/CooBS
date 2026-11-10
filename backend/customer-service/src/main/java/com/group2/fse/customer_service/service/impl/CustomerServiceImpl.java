package com.group2.fse.customer_service.service.impl;

import com.group2.fse.customer_service.dto.*;
import com.group2.fse.customer_service.entity.*;
import com.group2.fse.customer_service.exception.CustomerNotFoundException;
import com.group2.fse.customer_service.exception.KycNotFoundException;
import com.group2.fse.customer_service.exception.KycRequestNotFoundException;
import com.group2.fse.customer_service.repository.*;
import com.group2.fse.customer_service.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final KycRepository kycRepository;
    private final ChannelProfileRepository channelProfileRepository;
    private final KycUpdateRequestRepository kycUpdateRequestRepository;

    @Override
    @Transactional(readOnly = true)
    public CustomerProfileResponse getCustomerProfile(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with ID: " + customerId));

        Kyc kyc = kycRepository.findByCustomer(customer)
                .orElse(null);

        ChannelProfile profile = channelProfileRepository.findById(customerId)
                .orElse(null);

        return mapToCustomerProfileResponse(customer, kyc, profile);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerProfileResponse getCustomerById(Long customerId) {
        return getCustomerProfile(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public RiskProfileResponse getRiskProfile(Long customerId) {
        ChannelProfile profile = channelProfileRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Channel profile not found for customer ID: " + customerId));

        return RiskProfileResponse.builder()
                .customerId(profile.getCustomerId())
                .riskScore(profile.getRiskScore() != null ? profile.getRiskScore().toString() : "0.00")
                .kycTier(profile.getKycTier() != null ? profile.getKycTier() : "TIER_1_BASIC")
                .kycStatus(profile.getKycStatus() != null ? profile.getKycStatus() : "VERIFIED")
                .dailyLimit(profile.getDailyLimit() != null ? profile.getDailyLimit().setScale(2).toString() : "100000.00")
                .build();
    }

    @Override
    @Transactional
    public KycRequestResponse submitKyc(Long customerId, KycSubmitRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with ID: " + customerId));

        Kyc kyc = kycRepository.findByCustomer(customer).orElse(null);
        if (kyc == null) {
            kyc = Kyc.builder()
                    .customer(customer)
                    .firstName(request.getFirstName())
                    .middleInitial(request.getMiddleInitial())
                    .lastName(request.getLastName())
                    .address(request.getAddress())
                    .civilStatus(request.getCivilStatus())
                    .occupation(request.getOccupation())
                    .mobileNumber(request.getMobileNumber())
                    .status("VERIFIED")
                    .build();
        } else {
            kyc.setFirstName(request.getFirstName());
            kyc.setMiddleInitial(request.getMiddleInitial());
            kyc.setLastName(request.getLastName());
            kyc.setAddress(request.getAddress());
            kyc.setCivilStatus(request.getCivilStatus());
            kyc.setOccupation(request.getOccupation());
            kyc.setMobileNumber(request.getMobileNumber());
            kyc.setStatus("VERIFIED");
        }
        kycRepository.save(kyc);

        customer.setKycStatus("VERIFIED");
        customerRepository.save(customer);

        ChannelProfile profile = channelProfileRepository.findById(customerId).orElse(null);
        if (profile != null) {
            profile.setKycStatus("VERIFIED");
            channelProfileRepository.save(profile);
        }

        log.info("KYC verified for customer ID: {}", customerId);

        return KycRequestResponse.builder()
                .kycId(kyc.getKycId())
                .customerId(customerId)
                .status("VERIFIED")
                .message("KYC profile submitted and verified successfully.")
                .build();
    }

    @Override
    @Transactional
    public KycRequestResponse submitKycUpdateRequest(Long customerId, KycUpdateRequestDto request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with ID: " + customerId));

        Kyc kyc = kycRepository.findByCustomer(customer)
                .orElseThrow(() -> new KycNotFoundException("KYC record not found for customer ID: " + customerId));

        KycUpdateRequest updateReq = KycUpdateRequest.builder()
                .kyc(kyc)
                .newFirstName(request.getNewFirstName())
                .newMiddleInitial(request.getNewMiddleInitial())
                .newLastName(request.getNewLastName())
                .newAddress(request.getNewAddress())
                .newMobileNumber(request.getNewMobileNumber())
                .newCivilStatus(request.getNewCivilStatus())
                .newOccupation(request.getNewOccupation())
                .status("PENDING")
                .build();

        KycUpdateRequest saved = kycUpdateRequestRepository.save(updateReq);
        log.info("Submitted KYC update request #{} for customer ID {}", saved.getKycRequestId(), customerId);

        return KycRequestResponse.builder()
                .kycRequestId(saved.getKycRequestId())
                .kycId(kyc.getKycId())
                .customerId(customerId)
                .status(saved.getStatus())
                .requestedAt(saved.getRequestedAt())
                .message("KYC update request submitted for review.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<KycRequestResponse> getPendingKycUpdateRequests(String status, Pageable pageable) {
        Page<KycUpdateRequest> page = kycUpdateRequestRepository.findByStatus(status != null ? status : "PENDING", pageable);

        return PageResponse.<KycRequestResponse>builder()
                .content(page.map(req -> KycRequestResponse.builder()
                        .kycRequestId(req.getKycRequestId())
                        .kycId(req.getKyc().getKycId())
                        .customerId(req.getKyc().getCustomer().getCustomerId())
                        .status(req.getStatus())
                        .newFirstName(req.getNewFirstName())
                        .newMiddleInitial(req.getNewMiddleInitial())
                        .newLastName(req.getNewLastName())
                        .newAddress(req.getNewAddress())
                        .newMobileNumber(req.getNewMobileNumber())
                        .newCivilStatus(req.getNewCivilStatus())
                        .newOccupation(req.getNewOccupation())
                        .requestedAt(req.getRequestedAt())
                        .build()).getContent())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isLast(page.isLast())
                .build();
    }

    @Override
    @Transactional
    public KycRequestResponse approveKycUpdateRequest(Long requestId, Long staffUserId) {
        KycUpdateRequest updateReq = kycUpdateRequestRepository.findById(requestId)
                .orElseThrow(() -> new KycRequestNotFoundException("KYC update request not found: " + requestId));

        if (!"PENDING".equalsIgnoreCase(updateReq.getStatus())) {
            throw new IllegalStateException("KYC update request #" + requestId + " is already " + updateReq.getStatus());
        }

        Kyc kyc = updateReq.getKyc();
        if (updateReq.getNewFirstName() != null) kyc.setFirstName(updateReq.getNewFirstName());
        if (updateReq.getNewMiddleInitial() != null) kyc.setMiddleInitial(updateReq.getNewMiddleInitial());
        if (updateReq.getNewLastName() != null) kyc.setLastName(updateReq.getNewLastName());
        if (updateReq.getNewAddress() != null) kyc.setAddress(updateReq.getNewAddress());
        if (updateReq.getNewMobileNumber() != null) kyc.setMobileNumber(updateReq.getNewMobileNumber());
        if (updateReq.getNewCivilStatus() != null) kyc.setCivilStatus(updateReq.getNewCivilStatus());
        if (updateReq.getNewOccupation() != null) kyc.setOccupation(updateReq.getNewOccupation());
        kycRepository.save(kyc);

        updateReq.setStatus("APPROVED");
        updateReq.setApprovedBy(staffUserId);
        updateReq.setApprovedAt(LocalDateTime.now());
        kycUpdateRequestRepository.save(updateReq);

        log.info("Approved KYC update request #{} by staff ID {}", requestId, staffUserId);

        return KycRequestResponse.builder()
                .kycRequestId(updateReq.getKycRequestId())
                .kycId(kyc.getKycId())
                .customerId(kyc.getCustomer().getCustomerId())
                .status("APPROVED")
                .approvedBy(staffUserId)
                .approvedAt(updateReq.getApprovedAt())
                .message("KYC update request approved successfully.")
                .build();
    }

    @Override
    @Transactional
    public KycRequestResponse rejectKycUpdateRequest(Long requestId, Long staffUserId, String rejectionReason) {
        KycUpdateRequest updateReq = kycUpdateRequestRepository.findById(requestId)
                .orElseThrow(() -> new KycRequestNotFoundException("KYC update request not found: " + requestId));

        if (!"PENDING".equalsIgnoreCase(updateReq.getStatus())) {
            throw new IllegalStateException("KYC update request #" + requestId + " is already " + updateReq.getStatus());
        }

        updateReq.setStatus("REJECTED");
        updateReq.setApprovedBy(staffUserId);
        updateReq.setApprovedAt(LocalDateTime.now());
        updateReq.setRejectionReason(rejectionReason);
        kycUpdateRequestRepository.save(updateReq);

        log.info("Rejected KYC update request #{} by staff ID {}. Reason: {}", requestId, staffUserId, rejectionReason);

        return KycRequestResponse.builder()
                .kycRequestId(updateReq.getKycRequestId())
                .kycId(updateReq.getKyc().getKycId())
                .customerId(updateReq.getKyc().getCustomer().getCustomerId())
                .status("REJECTED")
                .approvedBy(staffUserId)
                .reviewedAt(updateReq.getApprovedAt())
                .rejectionReason(rejectionReason)
                .message("KYC update request rejected.")
                .build();
    }

    private CustomerProfileResponse mapToCustomerProfileResponse(Customer customer, Kyc kyc, ChannelProfile profile) {
        CustomerProfileResponse.KycDto kycDto = null;
        if (kyc != null) {
            kycDto = CustomerProfileResponse.KycDto.builder()
                    .kycId(kyc.getKycId())
                    .firstName(kyc.getFirstName())
                    .middleInitial(kyc.getMiddleInitial())
                    .lastName(kyc.getLastName())
                    .address(kyc.getAddress())
                    .mobileNumber(kyc.getMobileNumber())
                    .civilStatus(kyc.getCivilStatus())
                    .occupation(kyc.getOccupation())
                    .status(kyc.getStatus())
                    .createdAt(kyc.getCreatedAt())
                    .build();
        }

        String username = profile != null && profile.getUsername() != null ? profile.getUsername() : "customer_" + customer.getCustomerId();
        String email = profile != null && profile.getEmail() != null ? profile.getEmail() : null;
        String kycStatus = profile != null && profile.getKycStatus() != null ? profile.getKycStatus() : customer.getKycStatus();
        String membershipNumber = profile != null ? profile.getMembershipNumber() : String.format("MBR-%06d", customer.getCustomerId());
        String displayName = profile != null && profile.getDisplayName() != null ? profile.getDisplayName()
                : (kyc != null ? kyc.getFirstName() + " " + kyc.getLastName() : username);
        String kycTier = profile != null ? profile.getKycTier() : "TIER_3_FULL";

        return CustomerProfileResponse.builder()
                .customerId(customer.getCustomerId())
                .username(username)
                .email(email)
                .kycStatus(kycStatus)
                .createdAt(customer.getCreatedAt())
                .kyc(kycDto)
                .membershipNumber(membershipNumber)
                .displayName(displayName)
                .kycTier(kycTier)
                .build();
    }
}
