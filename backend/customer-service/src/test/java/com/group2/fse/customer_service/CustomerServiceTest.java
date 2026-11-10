package com.group2.fse.customer_service;

import com.group2.fse.customer_service.dto.*;
import com.group2.fse.customer_service.entity.*;
import com.group2.fse.customer_service.exception.CustomerNotFoundException;
import com.group2.fse.customer_service.repository.*;
import com.group2.fse.customer_service.service.impl.CustomerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private KycRepository kycRepository;

    @Mock
    private ChannelProfileRepository channelProfileRepository;

    @Mock
    private KycUpdateRequestRepository kycUpdateRequestRepository;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private Customer mockCustomer;
    private Kyc mockKyc;
    private ChannelProfile mockProfile;

    @BeforeEach
    void setUp() {
        mockCustomer = Customer.builder()
                .customerId(1L)
                .kycStatus("VERIFIED")
                .createdAt(LocalDateTime.now())
                .build();

        mockKyc = Kyc.builder()
                .kycId(101L)
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

        mockProfile = ChannelProfile.builder()
                .customerId(1L)
                .membershipNumber("MBR-000001")
                .username("john_doe")
                .displayName("John D.")
                .email("john.doe@example.com")
                .preferredLanguage("en")
                .kycTier("TIER_3_FULL")
                .kycStatus("VERIFIED")
                .riskScore(new BigDecimal("0.15"))
                .creditScore(750)
                .dailyLimit(new BigDecimal("100000.0000"))
                .build();
    }

    @Test
    @DisplayName("Should successfully retrieve customer profile (C1/C2)")
    void shouldGetCustomerProfileSuccessfully() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(mockCustomer));
        when(kycRepository.findByCustomer(mockCustomer)).thenReturn(Optional.of(mockKyc));
        when(channelProfileRepository.findById(1L)).thenReturn(Optional.of(mockProfile));

        CustomerProfileResponse response = customerService.getCustomerProfile(1L);

        assertNotNull(response);
        assertEquals(1L, response.getCustomerId());
        assertEquals("john_doe", response.getUsername());
        assertEquals("John D.", response.getDisplayName());
        assertEquals("MBR-000001", response.getMembershipNumber());
        assertEquals("TIER_3_FULL", response.getKycTier());
        assertNotNull(response.getKyc());
        assertEquals("John", response.getKyc().getFirstName());
    }

    @Test
    @DisplayName("Should throw CustomerNotFoundException when customer not found")
    void shouldThrowExceptionWhenCustomerNotFound() {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(CustomerNotFoundException.class, () -> customerService.getCustomerProfile(999L));
    }

    @Test
    @DisplayName("Should retrieve internal risk profile (C7)")
    void shouldGetRiskProfileSuccessfully() {
        when(channelProfileRepository.findById(1L)).thenReturn(Optional.of(mockProfile));

        RiskProfileResponse response = customerService.getRiskProfile(1L);

        assertNotNull(response);
        assertEquals(1L, response.getCustomerId());
        assertEquals("0.15", response.getRiskScore());
        assertEquals("TIER_3_FULL", response.getKycTier());
        assertEquals("100000.00", response.getDailyLimit());
    }

    @Test
    @DisplayName("Should submit KYC successfully (C8)")
    void shouldSubmitKycSuccessfully() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(mockCustomer));
        when(kycRepository.findByCustomer(mockCustomer)).thenReturn(Optional.of(mockKyc));
        when(channelProfileRepository.findById(1L)).thenReturn(Optional.of(mockProfile));
        when(kycRepository.save(any(Kyc.class))).thenReturn(mockKyc);

        KycSubmitRequest request = KycSubmitRequest.builder()
                .firstName("John")
                .middleInitial("D")
                .lastName("Doe")
                .address("New Address 456")
                .mobileNumber("+639171234567")
                .civilStatus("MARRIED")
                .occupation("Lead Architect")
                .build();

        KycRequestResponse response = customerService.submitKyc(1L, request);

        assertNotNull(response);
        assertEquals(1L, response.getCustomerId());
        assertEquals("VERIFIED", response.getStatus());
        verify(kycRepository).save(any(Kyc.class));
    }
}
