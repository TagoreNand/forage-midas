package com.jpmc.midascore.risk;

import com.jpmc.midascore.domain.model.Money;

import java.time.Instant;

/**
 * The features a {@link FraudScorer} sees for one transfer (Phase 5). Built from a
 * {@code TransferRecorded} event, so scoring runs off the event stream, fully decoupled from
 * the settlement write path.
 */
public record TransferContext(long senderId, long recipientId, Money amount, Instant occurredAt) {
}
