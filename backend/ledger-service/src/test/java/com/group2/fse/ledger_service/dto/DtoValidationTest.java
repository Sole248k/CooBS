package com.group2.fse.ledger_service.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DTO Validation Constraint Tests (FSE-403 & FSE-306)")
class DtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid TransferRequestDto should have no constraint violations")
    void validTransferRequestDto() {
        TransferRequestDto dto = TransferRequestDto.builder()
                .sourceAccountId(1L)
                .destinationAccountId(2L)
                .amount(new BigDecimal("5000.0000"))
                .referenceNo("TRF-20260926-001")
                .remarks("Valid transfer")
                .build();

        Set<ConstraintViolation<TransferRequestDto>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("TransferRequestDto with same source and destination account should fail validation")
    void sameSourceAndDestinationAccountFails() {
        TransferRequestDto dto = TransferRequestDto.builder()
                .sourceAccountId(1L)
                .destinationAccountId(1L)
                .amount(new BigDecimal("100.0000"))
                .referenceNo("TRF-SAME")
                .build();

        Set<ConstraintViolation<TransferRequestDto>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("distinct");
    }

    @Test
    @DisplayName("TransferRequestDto with negative or zero amount should fail validation")
    void negativeAmountFails() {
        TransferRequestDto dto = TransferRequestDto.builder()
                .sourceAccountId(1L)
                .destinationAccountId(2L)
                .amount(new BigDecimal("-50.0000"))
                .referenceNo("TRF-NEG")
                .build();

        Set<ConstraintViolation<TransferRequestDto>> violations = validator.validate(dto);
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("TransferRequestDto with > 4 decimal places should fail validation")
    void scaleExceedingFourFails() {
        TransferRequestDto dto = TransferRequestDto.builder()
                .sourceAccountId(1L)
                .destinationAccountId(2L)
                .amount(new BigDecimal("100.12345"))
                .referenceNo("TRF-SCALE")
                .build();

        Set<ConstraintViolation<TransferRequestDto>> violations = validator.validate(dto);
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("Valid DebitCreditRequestDto should have no constraint violations")
    void validDebitCreditRequestDto() {
        DebitCreditRequestDto dto = DebitCreditRequestDto.builder()
                .accountId(1L)
                .amount(new BigDecimal("2500.0000"))
                .referenceNo("DEB-20260926-001")
                .remarks("Valid debit")
                .build();

        Set<ConstraintViolation<DebitCreditRequestDto>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("DebitCreditRequestDto with missing referenceNo should fail validation")
    void missingReferenceNoFails() {
        DebitCreditRequestDto dto = DebitCreditRequestDto.builder()
                .accountId(1L)
                .amount(new BigDecimal("100.0000"))
                .referenceNo("")
                .build();

        Set<ConstraintViolation<DebitCreditRequestDto>> violations = validator.validate(dto);
        assertThat(violations).isNotEmpty();
    }
}
