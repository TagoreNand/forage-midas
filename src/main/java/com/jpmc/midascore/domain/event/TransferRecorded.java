package com.jpmc.midascore.domain.event;

import com.jpmc.midascore.domain.model.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * TransferRecorded — the domain event published when a transfer is successfully posted.
 *
 * <p>Pattern: Domain Event / Observer. This is the <strong>integration contract</strong>
 * between bounded contexts (ARCHITECTURE.md §1). The Ledger context emits it; the
 * Incentives, Risk/Fraud, Notifications, and Query (read-model) contexts subscribe — none
 * of which the Ledger knows about. Adding a new reaction to a settled transfer therefore
 * requires <em>zero</em> changes to the core write path (Open/Closed Principle).
 *
 * <p>It is written to the transactional <em>outbox</em> in the same DB transaction as the
 * postings, then relayed to Kafka, eliminating the dual-write problem (ARCHITECTURE.md §8).
 */
public record TransferRecorded(
        UUID transferId,
        long senderId,
        long recipientId,
        Money amount,
        Instant occurredAt) {

    public static TransferRecorded now(
            UUID transferId, long senderId, long recipientId, Money amount) {
        return new TransferRecorded(transferId, senderId, recipientId, amount, Instant.now());
    }
}
