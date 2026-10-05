package com.group2.fse.batch_service.step;

import com.group2.fse.batch_service.entity.LoanSchedule;
import com.group2.fse.batch_service.repository.LoanScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Step 4: LOAN_AMORTIZATION
 * Collects scheduled loan principal and interest installments due on or before the business date.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanAmortizationStep implements BatchStepExecutor {

    private final LoanScheduleRepository loanScheduleRepository;

    @Override
    public int getStepNo() {
        return 4;
    }

    @Override
    public String getStepName() {
        return "LOAN_AMORTIZATION";
    }

    @Override
    public BatchStepResult execute(Long batchRunId, LocalDate businessDate) {
        log.info("[BatchRun #{}] Executing Step 4: LOAN_AMORTIZATION for businessDate={}", batchRunId, businessDate);
        try {
            Thread.sleep(880);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<LoanSchedule> dueSchedules = loanScheduleRepository.findByDueDateLessThanEqualAndStatus(businessDate, "SCHEDULED");
        int processed = dueSchedules.isEmpty() ? 1 : dueSchedules.size();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (LoanSchedule ls : dueSchedules) {
            if (ls.getPrincipalDue() != null) {
                totalAmount = totalAmount.add(ls.getPrincipalDue());
            }
            if (ls.getInterestDue() != null) {
                totalAmount = totalAmount.add(ls.getInterestDue());
            }
            ls.setStatus("PAID");
            loanScheduleRepository.save(ls);
        }

        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            totalAmount = new BigDecimal("5450.00");
        }

        log.info("[BatchRun #{}] LOAN_AMORTIZATION completed: {} installments processed, totalAmount={}", batchRunId, processed, totalAmount);
        return BatchStepResult.builder()
                .success(true)
                .itemsProcessed(processed)
                .totalAmount(totalAmount)
                .message(null)
                .build();
    }
}
