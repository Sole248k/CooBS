package com.group2.fse.account_service.service.impl;

import com.group2.fse.account_service.dto.*;
import com.group2.fse.account_service.entity.Customer;
import com.group2.fse.account_service.entity.Kyc;
import com.group2.fse.account_service.entity.KycUpdateRequest;
import com.group2.fse.account_service.entity.User;
import com.group2.fse.account_service.event.AccountEventPublisher;
import com.group2.fse.account_service.event.KycRequestEvaluatedEvent;
import com.group2.fse.account_service.event.KycRequestSubmittedEvent;
import com.group2.fse.account_service.exception.CustomerNotFoundException;
import com.group2.fse.account_service.exception.KycNotFoundException;
import com.group2.fse.account_service.exception.KycRequestNotFoundException;
import com.group2.fse.account_service.repository.CustomerRepository;
import com.group2.fse.account_service.repository.KycRepository;
import com.group2.fse.account_service.repository.KycUpdateRequestRepository;
import com.group2.fse.account_service.repository.UserRepository;
import com.group2.fse.account_service.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final KycRepository kycRepository;
    private final KycUpdateRequestRepository kycUpdateRequestRepository;
    private final UserRepository userRepository;
    private final AccountEventPublisher accountEventPublisher;

    @Override
    @Transactional(readOnly = true)
    public CustomerProfileResponse getCustomerProfile(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with ID: " + customerId));

        Kyc kyc = kycRepository.findByCustomer(customer).orElse(null);
        return mapToCustomerProfileResponse(customer, kyc);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerProfileResponse getCustomerById(Long customerId) {
        return getCustomerProfile(customerId);
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
                .orElseThrow(() -> new KycNotFoundException("No existing KYC found for customer ID: " + customerId));

        KycUpdateRequest updateRequest = KycUpdateRequest.builder()
                .kyc(kyc)
                .newFirstName(request.getNewFirstName())
                .newMiddleInitial(request.getNewMiddleInitial())
                .newLastName(request.getNewLastName())
                .newAddress(request.getNewAddress())
                .newMobileNumber(request.getNewMobileNumber())
                .newCivilStatus(request.getNewCivilStatus())
                .newOccupation(request.getNewOccupation())
                .status("PENDING")
                .requestedAt(LocalDateTime.now())
                .build();

        KycUpdateRequest saved = kycUpdateRequestRepository.save(updateRequest);
        log.info("Submitted KYC update request #{} for customer ID {}", saved.getKycRequestId(), customerId);

        if (accountEventPublisher != null) {
            accountEventPublisher.publishKycSubmitted(KycRequestSubmittedEvent.builder()
                    .payload(KycRequestSubmittedEvent.KycSubmittedPayload.builder()
                            .requestId(saved.getKycRequestId())
                            .customerId(customerId)
                            .status(saved.getStatus())
                            .submittedAt(saved.getRequestedAt() != null ? saved.getRequestedAt().toString() : LocalDateTime.now().toString())
                            .build())
                    .build());
        }

        return KycRequestResponse.builder()
                .kycRequestId(saved.getKycRequestId())
                .kycId(kyc.getKycId())
                .customerId(customerId)
                .status("PENDING")
                .requestedAt(saved.getRequestedAt())
                .message("KYC update request submitted for teller approval.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<KycRequestResponse> getPendingKycUpdateRequests(String status, Pageable pageable) {
        String filterStatus = (status == null || status.isBlank()) ? "PENDING" : status.toUpperCase();
        Page<KycUpdateRequest> page = kycUpdateRequestRepository.findByStatus(filterStatus, pageable);

        Page<KycRequestResponse> responsePage = page.map(req -> KycRequestResponse.builder()
                .kycRequestId(req.getKycRequestId())
                .kycId(req.getKyc().getKycId())
                .customerId(req.getKyc().getCustomer().getCustomerId())
                .status(req.getStatus())
                .approvedBy(req.getApprovedBy() != null ? req.getApprovedBy().getUserId() : null)
                .requestedAt(req.getRequestedAt())
                .approvedAt(req.getApprovedAt())
                .newFirstName(req.getNewFirstName())
                .newMiddleInitial(req.getNewMiddleInitial())
                .newLastName(req.getNewLastName())
                .newAddress(req.getNewAddress())
                .newMobileNumber(req.getNewMobileNumber())
                .newCivilStatus(req.getNewCivilStatus())
                .newOccupation(req.getNewOccupation())
                .build());

        return PageResponse.from(responsePage);
    }

    @Override
    @Transactional
    public KycRequestResponse approveKycUpdateRequest(Long requestId, Long staffUserId) {
        KycUpdateRequest updateReq = kycUpdateRequestRepository.findById(requestId)
                .orElseThrow(() -> new KycRequestNotFoundException("KYC update request not found: " + requestId));

        if (!"PENDING".equalsIgnoreCase(updateReq.getStatus())) {
            throw new IllegalStateException("KYC update request is not in PENDING state (current: " + updateReq.getStatus() + ")");
        }

        User staffUser = staffUserId != null ? userRepository.findById(staffUserId).orElse(null) : null;

        Kyc kyc = updateReq.getKyc();
        if (updateReq.getNewFirstName() != null && !updateReq.getNewFirstName().isBlank()) {
            kyc.setFirstName(updateReq.getNewFirstName());
        }
        if (updateReq.getNewMiddleInitial() != null) {
            kyc.setMiddleInitial(updateReq.getNewMiddleInitial());
        }
        if (updateReq.getNewLastName() != null && !updateReq.getNewLastName().isBlank()) {
            kyc.setLastName(updateReq.getNewLastName());
        }
        if (updateReq.getNewAddress() != null && !updateReq.getNewAddress().isBlank()) {
            kyc.setAddress(updateReq.getNewAddress());
        }
        if (updateReq.getNewMobileNumber() != null && !updateReq.getNewMobileNumber().isBlank()) {
            kyc.setMobileNumber(updateReq.getNewMobileNumber());
        }
        if (updateReq.getNewCivilStatus() != null && !updateReq.getNewCivilStatus().isBlank()) {
            kyc.setCivilStatus(updateReq.getNewCivilStatus());
        }
        if (updateReq.getNewOccupation() != null && !updateReq.getNewOccupation().isBlank()) {
            kyc.setOccupation(updateReq.getNewOccupation());
        }
        kycRepository.save(kyc);

        updateReq.setStatus("APPROVED");
        updateReq.setApprovedBy(staffUser);
        updateReq.setApprovedAt(LocalDateTime.now());
        kycUpdateRequestRepository.save(updateReq);

        log.info("Approved KYC update request #{} by staff ID {}", requestId, staffUserId);

        Long custId = (kyc.getCustomer() != null) ? kyc.getCustomer().getCustomerId() : null;
        if (accountEventPublisher != null && custId != null) {
            accountEventPublisher.publishKycEvaluated(KycRequestEvaluatedEvent.builder()
                    .payload(KycRequestEvaluatedEvent.KycEvaluatedPayload.builder()
                            .requestId(updateReq.getKycRequestId())
                            .customerId(custId)
                            .status("APPROVED")
                            .reviewedByStaffId(staffUserId)
                            .reviewedAt(updateReq.getApprovedAt() != null ? updateReq.getApprovedAt().toString() : LocalDateTime.now().toString())
                            .rejectionReason(null)
                            .build())
                    .build());
        }

        return KycRequestResponse.builder()
                .kycRequestId(updateReq.getKycRequestId())
                .status("APPROVED")
                .approvedBy(staffUserId)
                .approvedAt(updateReq.getApprovedAt())
                .message("Customer KYC profile successfully updated.")
                .build();
    }

    @Override
    @Transactional
    public KycRequestResponse rejectKycUpdateRequest(Long requestId, Long staffUserId, String rejectionReason) {
        KycUpdateRequest updateReq = kycUpdateRequestRepository.findById(requestId)
                .orElseThrow(() -> new KycRequestNotFoundException("KYC update request not found: " + requestId));

        if (!"PENDING".equalsIgnoreCase(updateReq.getStatus())) {
            throw new IllegalStateException("KYC update request is not in PENDING state (current: " + updateReq.getStatus() + ")");
        }

        User staffUser = staffUserId != null ? userRepository.findById(staffUserId).orElse(null) : null;

        updateReq.setStatus("REJECTED");
        updateReq.setApprovedBy(staffUser);
        updateReq.setApprovedAt(LocalDateTime.now());
        kycUpdateRequestRepository.save(updateReq);

        log.info("Rejected KYC update request #{} by staff ID {}. Reason: {}", requestId, staffUserId, rejectionReason);

        Long custId = (updateReq.getKyc() != null && updateReq.getKyc().getCustomer() != null)
                ? updateReq.getKyc().getCustomer().getCustomerId()
                : null;
        if (accountEventPublisher != null && custId != null) {
            accountEventPublisher.publishKycEvaluated(KycRequestEvaluatedEvent.builder()
                    .payload(KycRequestEvaluatedEvent.KycEvaluatedPayload.builder()
                            .requestId(updateReq.getKycRequestId())
                            .customerId(custId)
                            .status("REJECTED")
                            .reviewedByStaffId(staffUserId)
                            .reviewedAt(updateReq.getApprovedAt() != null ? updateReq.getApprovedAt().toString() : LocalDateTime.now().toString())
                            .rejectionReason(rejectionReason)
                            .build())
                    .build());
        }

        return KycRequestResponse.builder()
                .kycRequestId(updateReq.getKycRequestId())
                .status("REJECTED")
                .approvedBy(staffUserId)
                .reviewedAt(updateReq.getApprovedAt())
                .rejectionReason(rejectionReason)
                .message("Customer KYC update request has been rejected.")
                .build();
    }

    private CustomerProfileResponse mapToCustomerProfileResponse(Customer customer, Kyc kyc) {
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

        return CustomerProfileResponse.builder()
                .customerId(customer.getCustomerId())
                .username(customer.getDisplayName())
                .email(null)
                .kycStatus(customer.getKycStatus())
                .createdAt(customer.getCreatedAt())
                .kyc(kycDto)
                .build();
    }
}
