package com.jpmc.midascore.adapter.out.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for the append-only ledger.
 *
 * <ul>
 *   <li>{@code existsByTransferId} - durable idempotency backstop (Phase 2).</li>
 *   <li>{@code totalMinor} - sum of every signed entry; the conservation invariant requires it to
 *       be 0 (used by the reconciliation control, Phase 5).</li>
 *   <li>{@code findByAccountIdOrderByCreatedAtDesc} - statement / history reads, newest first.</li>
 * </ul>
 */
public interface LedgerEntryRepository extends CrudRepository<LedgerEntryEntity, UUID> {

    boolean existsByTransferId(UUID transferId);

    @Query("select coalesce(sum(e.amountMinor), 0) from LedgerEntryEntity e")
    long totalMinor();

    List<LedgerEntryEntity> findByAccountIdOrderByCreatedAtDesc(long accountId, Limit limit);
}
