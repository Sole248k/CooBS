package com.group2.fse.batch_service.dto;

import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EodTriggerRequestDto {
    @PastOrPresent(message = "Business date cannot be in the future")
    private LocalDate businessDate;
}
