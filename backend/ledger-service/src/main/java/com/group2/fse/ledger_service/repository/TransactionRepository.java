package com.group2.fse.ledger_service.repository;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.group2.fse.ledger_service.entity.Transaction;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByReferenceNo(String referenceNo);

    boolean existsByReferenceNo(String referenceNo);

    List<Transaction> findByReferenceNoStartingWith(String prefix);

    boolean existsByReversesTransaction_TransactionId(Long transactionId);

    List<Transaction> findByReferenceNoInOrderByTransactionIdAsc(Collection<String> referenceNos);

    /** One journal row without its account loaded: what a repeated posting is compared with. */
    interface Leg {
        String getReferenceNo();

        Long getAccountId();

        java.math.BigDecimal getAmount();
    }

    @Query("select t.referenceNo as referenceNo, t.account.accountId as accountId, t.amount as amount "
            + "from Transaction t where t.referenceNo in :referenceNos")
    List<Leg> findLegsByReferenceNoIn(@Param("referenceNos") Collection<String> referenceNos);

    /**
     * The journal rows of one posting: the reference itself, or its debit and credit legs.
     * Exact matches only. A LIKE on the caller's string would let % and _ match other postings
     * and could pair the legs of two different transfers.
     */
    default List<Transaction> findByOriginalReferenceNo(String ref) {
        return findByReferenceNoInOrderByTransactionIdAsc(legReferences(ref));
    }

    /**
     * A leg is stored as the reference plus -D or -C, cut to fit VARCHAR2(36). The posting engine
     * cuts the reference at 33 characters and hold capture at 34, so both are looked for.
     */
    static Set<String> legReferences(String ref) {
        Set<String> references = new LinkedHashSet<>();
        references.add(ref);
        for (int keep : new int[] {33, 34}) {
            String base = ref.length() > keep ? ref.substring(0, keep) : ref;
            references.add(base + "-D");
            references.add(base + "-C");
        }
        return references;
    }
}
