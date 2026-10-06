package com.group2.fse.account_service.service;

import com.group2.fse.account_service.dto.*;
import com.group2.fse.account_service.entity.*;
import com.group2.fse.account_service.exception.CustomerNotFoundException;
import com.group2.fse.account_service.exception.KycNotFoundException;
import com.group2.fse.account_service.exception.KycRequestNotFoundException;
import com.group2.fse.account_service.repository.CustomerRepository;
import com.group2.fse.account_service.repository.KycRepository;
import com.group2.fse.account_service.repository.KycUpdateRequestRepository;
import com.group2.fse.account_service.repository.UserRepository;
import com.group2.fse.account_service.service.impl.CustomerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Customer & KYC Management Service Tests")
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private KycRepository kycRepository;

    @Mock
    private KycUpdateRequestRepository kycUpdateRequestRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private Customer mockCustomer;
    private Kyc mockKyc;

    @BeforeEach
    void setUp() {
        mockCustomer = Customer.builder()
                .customerId(101L)
                .kycStatus("VERIFIED")
                .createdAt(LocalDateTime.now())
                .build();

        mockKyc = Kyc.builder()
                .kycId(201L)
                .customer(mockCustomer)
                .firstName("John")
                .middleInitial("D")
                .lastName("Doe")
                .address("123 Ayala Ave, Makati City")
                .mobileNumber("+639171234567")
                .civilStatus("SINGLE")
                .occupation("Software Engineer")
                .status("VERIFIED")
                .createdAt(LocalDateTime.now())
                .build();
        mockCustomer.setKyc(mockKyc);
    }

    @Test
    @DisplayName("[CUST-001] Should successfully retrieve customer profile and KYC details")
    void shouldGetCustomerProfileSuccessfully() {
        when(customerRepository.findById(101L)).thenReturn(Optional.of(mockCustomer));
        when(kycRepository.findByCustomer(mockCustomer)).thenReturn(Optional.of(mockKyc));

        CustomerProfileResponse response = customerService.getCustomerProfile(101L);

        assertNotNull(response);
        assertEquals(101L, response.getCustomerId());
        assertEquals("John D Doe", response.getUsername());
        assertEquals("VERIFIED", response.getKycStatus());
        assertNotNull(response.getKyc());
        assertEquals("John", response.getKyc().getFirstName());
        assertEquals("123 Ayala Ave, Makati City", response.getKyc().getAddress());
        verify(customerRepository).findById(101L);
        verify(kycRepository).findByCustomer(mockCustomer);
    }

    @Test
    @DisplayName("[CUST-002] Should throw CustomerNotFoundException when customer ID does not exist")
    void shouldThrowExceptionWhenCustomerNotFound() {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(CustomerNotFoundException.class, () -> customerService.getCustomerProfile(999L));
        verify(customerRepository).findById(999L);
        verifyNoInteractions(kycRepository);
    }

    @Test
    @DisplayName("[CUST-003] Should submit and verify initial KYC document submission")
    void shouldSubmitKycSuccessfully() {
        mockCustomer.setKycStatus("PENDING");
        when(customerRepository.findById(101L)).thenReturn(Optional.of(mockCustomer));
        when(kycRepository.findByCustomer(mockCustomer)).thenReturn(Optional.empty());
        when(kycRepository.save(any(Kyc.class))).thenAnswer(i -> {
            Kyc k = i.getArgument(0);
            k.setKycId(202L);
            return k;
        });

        KycSubmitRequest request = KycSubmitRequest.builder()
                .firstName("John")
                .middleInitial("D")
                .lastName("Doe")
                .address("123 Ayala Ave")
                .mobileNumber("+639171234567")
                .civilStatus("SINGLE")
                .occupation("Engineer")
                .build();

        KycRequestResponse response = customerService.submitKyc(101L, request);

        assertNotNull(response);
        assertEquals("VERIFIED", response.getStatus());
        assertEquals(101L, response.getCustomerId());
        assertEquals("VERIFIED", mockCustomer.getKycStatus());
        verify(kycRepository).save(any(Kyc.class));
        verify(customerRepository).save(mockCustomer);
    }

    @Test
    @DisplayName("[CUST-004] Should submit staged KYC update request for branch review")
    void shouldSubmitKycUpdateRequestSuccessfully() {
        when(customerRepository.findById(101L)).thenReturn(Optional.of(mockCustomer));
        when(kycRepository.findByCustomer(mockCustomer)).thenReturn(Optional.of(mockKyc));
        when(kycUpdateRequestRepository.save(any(KycUpdateRequest.class))).thenAnswer(i -> {
            KycUpdateRequest r = i.getArgument(0);
            r.setKycRequestId(55L);
            return r;
        });

        KycUpdateRequestDto requestDto = KycUpdateRequestDto.builder()
                .newAddress("456 BGC High Street, Taguig City")
                .newMobileNumber("+639189998888")
                .newOccupation("Senior Architect")
                .build();

        KycRequestResponse response = customerService.submitKycUpdateRequest(101L, requestDto);

        assertNotNull(response);
        assertEquals(55L, response.getKycRequestId());
        assertEquals("PENDING", response.getStatus());
        verify(kycUpdateRequestRepository).save(any(KycUpdateRequest.class));
    }

    @Test
    @DisplayName("[CUST-005] Should list pending KYC update requests with pagination")
    void shouldListPendingKycUpdateRequests() {
        KycUpdateRequest updateReq = KycUpdateRequest.builder()
                .kycRequestId(55L)
                .kyc(mockKyc)
                .newAddress("456 BGC High Street")
                .status("PENDING")
                .requestedAt(LocalDateTime.now())
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<KycUpdateRequest> page = new PageImpl<>(List.of(updateReq), pageable, 1);
        when(kycUpdateRequestRepository.findByStatus("PENDING", pageable)).thenReturn(page);

        PageResponse<KycRequestResponse> response = customerService.getPendingKycUpdateRequests("PENDING", pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(55L, response.getContent().get(0).getKycRequestId());
        assertEquals("PENDING", response.getContent().get(0).getStatus());
    }

    @Test
    @DisplayName("[CUST-006] Should approve KYC update request and merge details into master KYC table")
    void shouldApproveKycUpdateRequestSuccessfully() {
        KycUpdateRequest updateReq = KycUpdateRequest.builder()
                .kycRequestId(55L)
                .kyc(mockKyc)
                .newAddress("456 BGC High Street, Taguig City")
                .newOccupation("VP of Engineering")
                .status("PENDING")
                .build();

        User staffUser = User.builder().userId(2L).username("teller_alice").build();
        when(kycUpdateRequestRepository.findById(55L)).thenReturn(Optional.of(updateReq));
        when(userRepository.findById(2L)).thenReturn(Optional.of(staffUser));

        KycRequestResponse response = customerService.approveKycUpdateRequest(55L, 2L);

        assertNotNull(response);
        assertEquals("APPROVED", response.getStatus());
        assertEquals(2L, response.getApprovedBy());
        assertEquals("456 BGC High Street, Taguig City", mockKyc.getAddress());
        assertEquals("VP of Engineering", mockKyc.getOccupation());
        verify(kycRepository).save(mockKyc);
        verify(kycUpdateRequestRepository).save(updateReq);
    }

    @Test
    @DisplayName("[CUST-007] Should reject KYC update request with formal reason")
    void shouldRejectKycUpdateRequestSuccessfully() {
        KycUpdateRequest updateReq = KycUpdateRequest.builder()
                .kycRequestId(55L)
                .kyc(mockKyc)
                .status("PENDING")
                .build();

        User staffUser = User.builder().userId(2L).username("teller_alice").build();
        when(kycUpdateRequestRepository.findById(55L)).thenReturn(Optional.of(updateReq));
        when(userRepository.findById(2L)).thenReturn(Optional.of(staffUser));

        KycRequestResponse response = customerService.rejectKycUpdateRequest(55L, 2L, "Proof of billing address unreadable");

        assertNotNull(response);
        assertEquals("REJECTED", response.getStatus());
        assertEquals("Proof of billing address unreadable", response.getRejectionReason());
        verify(kycUpdateRequestRepository).save(updateReq);
        verify(kycRepository, never()).save(any(Kyc.class));
    }
}
