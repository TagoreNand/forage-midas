package com.jpmc.midascore.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Driving port for account statements (Phase 5 reporting): the recent ledger activity for an
 * account, derived from the immutable ledger rather than a mutable balance column.
 */
public interface GetStatementUseCase {

    Statement statementFor(long accountId, int limit);

    record Statement(long accountId, List<Entry> entries) {
    }

    record Entry(String transferId, BigDecimal amount, String currency, Instant at) {
    }
}
