package com.jpmc.midascore.domain.model.ledger;

import com.jpmc.midascore.domain.model.Money;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * LedgerEntry — one immutable, append-only posting to a single account.
 *
 * <p>Resolves Findings F2/F3: balances stop being a mutable column and become a
 * <em>projection</em> of these entries. Every transfer writes a <strong>balanced pair</strong>
 * (a debit and a credit of equal magnitude grouped by {@code transferId}), so the
 * conservation-of-value invariant {@code SUM(all entries) == 0} is checkable at any time —
 * the foundation of audit, dispute resolution, and nightly reconciliation.
 */
public record LedgerEntry(
        UUID id,
        long accountId,
        Money amount,        // signed: negative = debit, positive = credit
        UUID transferId,     // groups the debit+credit pair
        Instant createdAt) {

    public enum Direction { DEBIT, CREDIT }

    /**
     * Factory (GoF Factory): produce the balanced double-entry pair for a transfer.
     * This is the only sanctioned way to create entries, guaranteeing they always balance.
     */
    public static List<LedgerEntry> postingFor(
            UUID transferId, long senderId, long recipientId, Money amount) {
        Instant now = Instant.now();
        LedgerEntry debit = new LedgerEntry(
                UUID.randomUUID(), senderId, amount.negated(), transferId, now);
        LedgerEntry credit = new LedgerEntry(
                UUID.randomUUID(), recipientId, amount, transferId, now);
        return List.of(debit, credit);
    }

    public Direction direction() {
        return amount.isNegative() ? Direction.DEBIT : Direction.CREDIT;
    }
}
