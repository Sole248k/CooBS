package com.group2.fse.batch_service.step;

import com.group2.fse.batch_service.entity.DailyAuditSeal;
import com.group2.fse.batch_service.repository.DailyAuditSealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Step 6: AUDIT_SEAL
 * Computes the closing SHA-256 daily cryptographic seal and writes to audit_store.daily_audit_seal.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditSealStep implements BatchStepExecutor {

    private final DailyAuditSealRepository dailyAuditSealRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public int getStepNo() {
        return 6;
    }

    @Override
    public String getStepName() {
        return "AUDIT_SEAL";
    }

    @Override
    public BatchStepResult execute(Long batchRunId, LocalDate businessDate) {
        log.info("[BatchRun #{}] Executing Step 6: AUDIT_SEAL for businessDate={}", batchRunId, businessDate);

        // A day that is over is sealed once: a seal that a later run could rewrite would prove
        // nothing. Today is still open, so a later run today seals again over the rows added since.
        DailyAuditSeal existing = dailyAuditSealRepository.findByBusinessDate(businessDate).orElse(null);
        if (existing != null && businessDate.isBefore(LocalDate.now())) {
            log.info("[BatchRun #{}] AUDIT_SEAL: businessDate={} is already sealed, left as it is", batchRunId, businessDate);
            return sealed(existing.getSealHash(), " (already sealed)");
        }

        // The last audit row of the business date, not the newest row there is. A failed query
        // fails the step: there is nothing honest to seal without it.
        var rows = jdbcTemplate.query(
                "SELECT audit_id, current_hash FROM audit_store.ledger_mutation_audit " +
                "WHERE DATE(event_timestamp) <= ? ORDER BY audit_id DESC LIMIT 1",
                (rs, rowNum) -> new Object[]{rs.getLong("audit_id"), rs.getString("current_hash")},
                java.sql.Date.valueOf(businessDate)
        );
        if (rows.isEmpty()) {
            return BatchStepResult.builder()
                    .success(false)
                    .itemsProcessed(0)
                    .totalAmount(new BigDecimal("0.00"))
                    .message("No audit rows on or before " + businessDate + " to seal")
                    .build();
        }
        Long lastAuditId = (Long) rows.get(0)[0];
        String lastHash = (String) rows.get(0)[1];

        String sealHash = sha256(businessDate + ":" + lastAuditId + ":" + lastHash);

        DailyAuditSeal seal = existing != null ? existing : DailyAuditSeal.builder().businessDate(businessDate).build();
        seal.setLastAuditId(lastAuditId);
        seal.setSealHash(sealHash);
        seal.setSealedAt(Instant.now());
        dailyAuditSealRepository.save(seal);

        log.info("[BatchRun #{}] AUDIT_SEAL completed: businessDate={}, lastAuditId={}, sealHash={}",
                batchRunId, businessDate, lastAuditId, sealHash);
        return sealed(sealHash, "");
    }

    private static BatchStepResult sealed(String sealHash, String note) {
        String shortHash = sealHash.length() > 8 ? sealHash.substring(0, 8) : sealHash;
        return BatchStepResult.builder()
                .success(true)
                .itemsProcessed(1)
                .totalAmount(new BigDecimal("0.00"))
                .message("Seal " + shortHash + "…" + note)
                .build();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm missing", e);
        }
    }
}
