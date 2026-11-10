package com.group2.fse.batch_service.step;

import com.group2.fse.batch_service.entity.LoanSchedule;
import com.group2.fse.batch_service.repository.LoanScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Step 4: LOAN_AMORTIZATION
 * Collects the instalments due on or before the business date: debit the borrower's deposit
 * account, credit 9004 GL_LOAN_RECEIVABLE, through the ledger (L8). A schedule is marked PAID
 * only when the ledger says its instalment is in the journal; one it refuses (not enough money,
 * for one) stays due and is tried again on the next run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanAmortizationStep implements BatchStepExecutor {

    private final LoanScheduleRepository loanScheduleRepository;
    private final LedgerAuditHelper ledgerAuditHelper;
    private final LedgerPostings ledgerPostings;

    private static final Long GL_LOAN_RECEIVABLE = 9004L;

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

        List<LoanSchedule> dueSchedules = loanScheduleRepository.findByDueDateLessThanEqualAndStatusIn(
                businessDate, List.of("PENDING", "DUE", "SCHEDULED"));

        // One reference per schedule, whatever day it is collected on, so an instalment can be
        // posted once only.
        List<LoanSchedule> billed = new ArrayList<>();
        List<LedgerPostings.Posting> postings = new ArrayList<>();
        for (LoanSchedule schedule : dueSchedules) {
            BigDecimal principal = schedule.getPrincipalDue() != null ? schedule.getPrincipalDue() : BigDecimal.ZERO;
            BigDecimal interest = schedule.getInterestDue() != null ? schedule.getInterestDue() : BigDecimal.ZERO;
            BigDecimal instalment = principal.add(interest);
            if (instalment.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            billed.add(schedule);
            postings.add(new LedgerPostings.Posting(referenceOf(schedule),
                    ledgerAuditHelper.getBorrowerDepositAccount(schedule.getLoanAccountId()),
                    GL_LOAN_RECEIVABLE, instalment, "Loan instalment, schedule " + schedule.getScheduleId()));
        }

        Map<String, String> outcomes = ledgerPostings.post(batchRunId, getStepName(), postings);

        int collected = 0;
        int refused = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int i = 0; i < billed.size(); i++) {
            LoanSchedule schedule = billed.get(i);
            LedgerPostings.Posting posting = postings.get(i);
            String errorCode = outcomes.get(posting.referenceNo());
            if (errorCode != null) {
                refused++;
                log.warn("[BatchRun #{}] Instalment #{} from account #{} refused by the ledger: {}; it stays due",
                        batchRunId, schedule.getScheduleId(), posting.debitAccountId(), errorCode);
                continue;
            }
            schedule.setStatus("PAID");
            loanScheduleRepository.save(schedule);
            collected++;
            totalAmount = totalAmount.add(posting.amount());
        }

        String message = collected == 0 && refused == 0
                ? "No instalments due"
                : "Collected " + collected + " instalments (credited GL 9004)"
                        + (refused > 0 ? "; " + refused + " refused by the ledger and still due" : "");
        log.info("[BatchRun #{}] LOAN_AMORTIZATION completed: {} collected, {} refused, totalAmount={}",
                batchRunId, collected, refused, totalAmount);
        return BatchStepResult.builder()
                .success(true)
                .itemsProcessed(collected)
                .totalAmount(totalAmount)
                .message(message)
                .build();
    }

    private static String referenceOf(LoanSchedule schedule) {
        return "EOD-LN-" + schedule.getScheduleId();
    }
}
