package com.group2.fse.batch_service.step;

import com.group2.fse.batch_service.entity.StandingInstruction;
import com.group2.fse.batch_service.repository.StandingInstructionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Step 3: STANDING_INSTRUCTIONS
 * Executes the recurring transfers due on or before the business date: debit the source
 * account, credit the destination, through the ledger (L8). An instruction moves to its next
 * date only when the ledger says the transfer is in the journal; one it refuses stays due and
 * is tried again on the next run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StandingInstructionsStep implements BatchStepExecutor {

    private final StandingInstructionRepository standingInstructionRepository;
    private final LedgerPostings ledgerPostings;

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

        List<StandingInstruction> dueInstructions = standingInstructionRepository.findByNextRunDateLessThanEqualAndActiveTrue(businessDate);

        // One reference per instruction and due date, so one occurrence can be posted once only.
        List<StandingInstruction> due = new ArrayList<>();
        List<LedgerPostings.Posting> postings = new ArrayList<>();
        for (StandingInstruction si : dueInstructions) {
            if (si.getAmount() == null || si.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            due.add(si);
            postings.add(new LedgerPostings.Posting(
                    "EOD-SI-" + si.getInstructionId() + "-" + si.getNextRunDate().format(DateTimeFormatter.BASIC_ISO_DATE),
                    si.getSourceAccountId(), si.getDestinationAccountId(), si.getAmount(),
                    "Standing instruction " + si.getInstructionId()));
        }

        Map<String, String> outcomes = ledgerPostings.post(batchRunId, getStepName(), postings);

        int executed = 0;
        int refused = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int i = 0; i < due.size(); i++) {
            StandingInstruction si = due.get(i);
            LedgerPostings.Posting posting = postings.get(i);
            String errorCode = outcomes.get(posting.referenceNo());
            if (errorCode != null) {
                refused++;
                log.warn("[BatchRun #{}] Standing instruction #{} refused by the ledger: {}; it stays due",
                        batchRunId, si.getInstructionId(), errorCode);
                continue;
            }
            si.setNextRunDate(si.getNextRunDate().plusMonths(1));
            standingInstructionRepository.save(si);
            executed++;
            totalAmount = totalAmount.add(posting.amount());
        }

        String message = executed == 0 && refused == 0
                ? "No instructions due"
                : "Executed " + executed + " recurring instructions"
                        + (refused > 0 ? "; " + refused + " refused by the ledger and still due" : "");
        log.info("[BatchRun #{}] STANDING_INSTRUCTIONS completed: {} executed, {} refused, totalAmount={}",
                batchRunId, executed, refused, totalAmount);
        return BatchStepResult.builder()
                .success(true)
                .itemsProcessed(executed)
                .totalAmount(totalAmount)
                .message(message)
                .build();
    }
}
