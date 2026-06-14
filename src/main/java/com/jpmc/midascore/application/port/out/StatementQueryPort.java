package com.jpmc.midascore.application.port.out;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port for reading ledger history for statements (CQRS query side). Returns raw,
 * minor-unit ledger rows; the application service maps them to the {@code Money}-shaped API.
 */
public interface StatementQueryPort {

    List<LedgerView> recentEntries(long accountId, int limit);

    record LedgerView(String transferId, long amountMinor, String currency, Instant at) {
    }
}
