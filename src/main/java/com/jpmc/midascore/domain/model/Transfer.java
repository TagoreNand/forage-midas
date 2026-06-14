package com.jpmc.midascore.domain.model;

import java.util.UUID;

/**
 * Transfer — an intent to move {@code amount} from sender to recipient.
 *
 * <p>Resolves Finding F2 (the baseline {@code Transaction} is never persisted) and F5
 * (no idempotency). The {@code id} is the <strong>idempotency key</strong>: it is checked
 * against Redis (fast path) and the durable primary key before any posting, so Kafka's
 * at-least-once redelivery cannot double-post the same transfer (ARCHITECTURE.md §8).
 *
 * <p>A record: immutable by construction, value-based equality, no boilerplate.
 */
public record Transfer(
        UUID id,
        long senderId,
        long recipientId,
        Money amount) {

    public Transfer {
        if (senderId == recipientId) {
            throw new IllegalArgumentException("Self-transfer is not allowed"); // Finding F8
        }
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
    }

    /** Factory for inbound transfers that arrive without a pre-assigned id. */
    public static Transfer create(long senderId, long recipientId, Money amount) {
        return new Transfer(UUID.randomUUID(), senderId, recipientId, amount);
    }
}
