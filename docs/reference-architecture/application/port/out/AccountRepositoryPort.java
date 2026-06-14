package com.jpmc.midascore.application.port.out;

import com.jpmc.midascore.domain.model.Account;
import com.jpmc.midascore.domain.model.ledger.LedgerEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven port (outbound) for persistence. Implemented by a JPA adapter the domain never
 * imports — this is the structural fix for the leaky {@code DatabaseConduit} (Finding F12).
 *
 * <p>Interface Segregation (SOLID's I): narrow, intention-revealing operations rather than
 * a fat CRUD repository. The application asks for exactly what it needs to settle a transfer.
 */
public interface AccountRepositoryPort {

    Optional<Account> findById(long id);

    /** Persists balance changes under optimistic locking; throws on version conflict. */
    void saveAll(List<Account> accounts);

    /** Appends the immutable double-entry pair (never updates an existing entry). */
    void appendLedger(List<LedgerEntry> entries);

    /** Durable idempotency backstop: true if this transfer was already posted. */
    boolean transferExists(UUID transferId);
}
