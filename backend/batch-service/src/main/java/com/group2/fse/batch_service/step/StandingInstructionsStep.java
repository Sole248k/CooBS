package com.group2.fse.batch_service.step;

import com.group2.fse.batch_service.entity.StandingInstruction;
import com.group2.fse.batch_service.repository.StandingInstructionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Step 3: STANDING_INSTRUCTIONS
 * Executes scheduled recurring transfers due on or before the business date.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StandingInstructionsStep implements BatchStepExecutor {

    private final StandingInstructionRepository standingInstructionRepository;

    @Override
    public int getStepNo() {
        return 3;
    }

    @Override
    public String getStepName() {
        return "STANDING_INSTRUCTIONS";
    }

    @Override
    public BatchStepResult execute(Long batchRunId, LocalDate businessDate) {
        log.info("[BatchRun #{}] Executing Step 3: STANDING_INSTRUCTIONS for businessDate={}", batchRunId, businessDate);
        try {
            Thread.sleep(910);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<StandingInstruction> dueInstructions = standingInstructionRepository.findByNextRunDateLessThanEqualAndActiveTrue(businessDate);
        int processed = dueInstructions.size();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (StandingInstruction si : dueInstructions) {
            if (si.getAmount() != null) {
                totalAmount = totalAmount.add(si.getAmount());
            }
            si.setNextRunDate(si.getNextRunDate().plusMonths(1));
            standingInstructionRepository.save(si);
        }

        String message = (processed > 0)
                ? ("Executed " + processed + " recurring instructions")
                : "No instructions due";

        log.info("[BatchRun #{}] STANDING_INSTRUCTIONS completed: {} orders executed, totalAmount={}", batchRunId, processed, totalAmount);
        return BatchStepResult.builder()
                .success(true)
                .itemsProcessed(processed)
                .totalAmount(totalAmount)
                .message(message)
                .build();
    }
}
