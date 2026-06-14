package com.jpmc.midascore.adapter.out.persistence;

import com.jpmc.midascore.application.port.out.StatementQueryPort;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Persistence adapter implementing {@link StatementQueryPort} by reading the append-only ledger
 * newest-first via {@link LedgerEntryRepository}.
 */
@Component
public class LedgerStatementAdapter implements StatementQueryPort {

    private final LedgerEntryRepository ledger;

    public LedgerStatementAdapter(LedgerEntryRepository ledger) {
        this.ledger = ledger;
    }

    @Override
    public List<LedgerView> recentEntries(long accountId, int limit) {
        return ledger.findByAccountIdOrderByCreatedAtDesc(accountId, Limit.of(limit)).stream()
                .map(entry -> new LedgerView(
                        entry.getTransferId().toString(),
                        entry.getAmountMinor(),
                        entry.getCurrency(),
                        entry.getCreatedAt()))
                .toList();
    }
}
