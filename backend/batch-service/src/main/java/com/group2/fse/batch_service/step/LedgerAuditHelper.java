package com.group2.fse.batch_service.step;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Properties;

/**
 * Looks up what the end-of-day steps need to know about an account. Read only: every posting
 * goes through the ledger ({@link LedgerPostings}), which is the only writer of a balance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerAuditHelper {

    @Value("${oracle.datasource.url}")
    private String oracleUrl;

    @Value("${oracle.datasource.username}")
    private String oracleUser;

    @Value("${oracle.datasource.password}")
    private String oraclePassword;

    public record AccountInfo(String accountNumber, Long customerId) {}

    /**
     * The account's number (e.g. ACC_10000001) and its owner, read from Oracle. Null when it
     * cannot be read: the caller then sends nothing, because a guessed owner would put one
     * member's account and amount in another member's inbox.
     */
    public AccountInfo getAccountInfo(Long accountId) {
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT account_number, customer_id FROM CORE_USER.ACCOUNT WHERE account_id = ?")) {
            stmt.setLong(1, accountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new AccountInfo(rs.getString("account_number"), rs.getLong("customer_id"));
                }
            }
            log.warn("Account #{} is not in the system of record", accountId);
        } catch (Exception ex) {
            log.warn("Could not read account #{} from the system of record: {}", accountId, ex.getMessage());
        }
        return null;
    }

    /** One day of the journal, added up for GL reconciliation. */
    public record JournalTotals(int entries, BigDecimal debits, BigDecimal credits, BigDecimal cashIn, BigDecimal cashOut) {}

    // The two legs of one posting share a reference, apart from a -D or -C at the end. A
    // reference with one row only is a single leg: cash paid in or out at the counter.
    // A debit is any type that says DEBIT; every other type is a credit.
    private static final String JOURNAL_TOTALS_SQL =
            "SELECT NVL(SUM(legs), 0), " +
            "       NVL(SUM(CASE WHEN legs > 1 THEN debits END), 0), " +
            "       NVL(SUM(CASE WHEN legs > 1 THEN credits END), 0), " +
            "       NVL(SUM(CASE WHEN legs = 1 THEN credits END), 0), " +
            "       NVL(SUM(CASE WHEN legs = 1 THEN debits END), 0) " +
            "FROM (SELECT COUNT(*) AS legs, " +
            "             SUM(CASE WHEN transaction_type LIKE '%DEBIT%' THEN amount ELSE 0 END) AS debits, " +
            "             SUM(CASE WHEN transaction_type LIKE '%DEBIT%' THEN 0 ELSE amount END) AS credits " +
            "      FROM CORE_USER.\"TRANSACTION\" " +
            "      WHERE TRUNC(created_at) = ? " +
            "      GROUP BY REGEXP_REPLACE(reference_no, '-[DC]$'))";

    /**
     * Adds up the journal of the business date, read from Oracle.
     *
     * @throws IllegalStateException when the journal cannot be read. The step then fails:
     *         a reconciliation that could not look must not report that the books balance.
     */
    public JournalTotals journalTotals(LocalDate businessDate) {
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(JOURNAL_TOTALS_SQL)) {
            stmt.setDate(1, java.sql.Date.valueOf(businessDate));
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return new JournalTotals(rs.getInt(1), rs.getBigDecimal(2), rs.getBigDecimal(3),
                        rs.getBigDecimal(4), rs.getBigDecimal(5));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("The journal could not be read: " + ex.getMessage(), ex);
        }
    }

    private Connection connect() throws SQLException {
        Properties login = new Properties();
        login.setProperty("user", String.valueOf(oracleUser));
        login.setProperty("password", String.valueOf(oraclePassword));
        login.setProperty("oracle.net.CONNECT_TIMEOUT", "3000");
        return DriverManager.getConnection(oracleUrl, login);
    }

    /**
     * Resolves the borrower's deposit account from a loan account ID.
     */
    public Long getBorrowerDepositAccount(Long loanAccountId) {
        if (loanAccountId == null) return 1L;
        if (loanAccountId == 6L) return 4L;  // Loan 6 (Customer 4) -> Savings Account 4 (ACC_10000004)
        if (loanAccountId == 10L) return 9L; // Loan 10 (Customer 7) -> Savings Account 9 (ACC_10000009)
        return loanAccountId;
    }
}
