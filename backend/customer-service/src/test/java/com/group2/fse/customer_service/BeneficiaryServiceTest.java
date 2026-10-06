package com.group2.fse.customer_service;

import com.group2.fse.customer_service.dto.BeneficiaryDto;
import com.group2.fse.customer_service.dto.CreateBeneficiaryRequest;
import com.group2.fse.customer_service.entity.Beneficiary;
import com.group2.fse.customer_service.exception.BeneficiaryNotFoundException;
import com.group2.fse.customer_service.repository.BeneficiaryRepository;
import com.group2.fse.customer_service.service.impl.BeneficiaryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BeneficiaryServiceTest {

    @Mock
    private BeneficiaryRepository beneficiaryRepository;

    @InjectMocks
    private BeneficiaryServiceImpl beneficiaryService;

    private Beneficiary mockBeneficiary;

    @BeforeEach
    void setUp() {
        mockBeneficiary = Beneficiary.builder()
                .beneficiaryId(11L)
                .customerId(1L)
                .nickname("Maria")
                .accountNumber("ACC_10000002")
                .bankCode("COOBS")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should list active beneficiaries for customer (C4)")
    void shouldListActiveBeneficiaries() {
        when(beneficiaryRepository.findByCustomerIdAndActiveTrue(1L)).thenReturn(List.of(mockBeneficiary));

        List<BeneficiaryDto> list = beneficiaryService.getBeneficiaries(1L);

        assertEquals(1, list.size());
        assertEquals("Maria", list.get(0).getNickname());
        assertEquals("ACC_10000002", list.get(0).getAccountNumber());
        assertEquals("COOBS", list.get(0).getBankCode());
        assertTrue(list.get(0).getActive());
    }

    @Test
    @DisplayName("Should add new beneficiary successfully (C5)")
    void shouldAddBeneficiarySuccessfully() {
        when(beneficiaryRepository.findByCustomerIdAndActiveTrue(1L)).thenReturn(List.of());
        when(beneficiaryRepository.save(any(Beneficiary.class))).thenReturn(mockBeneficiary);

        CreateBeneficiaryRequest request = CreateBeneficiaryRequest.builder()
                .nickname("Maria")
                .accountNumber("ACC_10000002")
                .bankCode("COOBS")
                .build();

        BeneficiaryDto result = beneficiaryService.addBeneficiary(1L, request);

        assertNotNull(result);
        assertEquals("Maria", result.getNickname());
        assertEquals("ACC_10000002", result.getAccountNumber());
        verify(beneficiaryRepository).save(any(Beneficiary.class));
    }

    @Test
    @DisplayName("Should deactivate beneficiary on delete (C6)")
    void shouldDeactivateBeneficiaryOnDelete() {
        when(beneficiaryRepository.findByBeneficiaryIdAndCustomerId(11L, 1L)).thenReturn(Optional.of(mockBeneficiary));

        beneficiaryService.deleteBeneficiary(1L, 11L);

        assertFalse(mockBeneficiary.getActive());
        verify(beneficiaryRepository).save(mockBeneficiary);
    }

    @Test
    @DisplayName("Should throw exception when beneficiary not found")
    void shouldThrowExceptionWhenBeneficiaryNotFound() {
        when(beneficiaryRepository.findByBeneficiaryIdAndCustomerId(999L, 1L)).thenReturn(Optional.empty());

        assertThrows(BeneficiaryNotFoundException.class, () -> beneficiaryService.deleteBeneficiary(1L, 999L));
    }
}
