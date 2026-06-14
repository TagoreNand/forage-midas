package com.jpmc.midascore.application.service;

import com.jpmc.midascore.application.port.in.GetStatementUseCase;
import com.jpmc.midascore.application.port.out.StatementQueryPort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Builds an account {@link Statement} from immutable ledger entries (Phase 5). Maps the stored
 * signed minor units back to a decimal {@code amount} and caps the page size defensively.
 */
@Service
public class StatementService implements GetStatementUseCase {

    private static final int MAX_LIMIT = 500;

    private final StatementQueryPort query;

    public StatementService(StatementQueryPort query) {
        this.query = query;
    }

    @Override
    public Statement statementFor(long accountId, int limit) {
        int capped = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<Entry> entries = query.recentEntries(accountId, capped).stream()
                .map(view -> new Entry(
                        view.transferId(),
                        BigDecimal.valueOf(view.amountMinor()).movePointLeft(2),
                        view.currency(),
                        view.at()))
                .toList();
        return new Statement(accountId, entries);
    }
}
