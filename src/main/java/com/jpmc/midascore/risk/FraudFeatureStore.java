package com.jpmc.midascore.risk;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory feature store backing the stateful scorers (velocity, new-recipient). It records, per
 * sender, recent transfer timestamps and the set of recipients ever paid.
 *
 * <p>This is a single-node approximation. A production deployment computes these features with
 * <strong>Kafka Streams</strong> windowed state stores (or Redis with TTL) so they are shared
 * across replicas and bounded in memory — the {@link FraudScorer} interface stays identical.
 */
@Component
public class FraudFeatureStore {

    private final Map<Long, Deque<Instant>> recentBySender = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> recipientsBySender = new ConcurrentHashMap<>();

    /** Record a transfer AFTER it has been scored, so scorers see only prior history. */
    public void record(TransferContext context) {
        recentBySender
                .computeIfAbsent(context.senderId(), k -> new ConcurrentLinkedDeque<>())
                .add(context.occurredAt());
        recipientsBySender
                .computeIfAbsent(context.senderId(), k -> ConcurrentHashMap.newKeySet())
                .add(context.recipientId());
    }

    /** Number of transfers by {@code senderId} within {@code window} ending at {@code now}. */
    public long recentCount(long senderId, Duration window, Instant now) {
        Deque<Instant> timestamps = recentBySender.get(senderId);
        if (timestamps == null) {
            return 0;
        }
        Instant cutoff = now.minus(window);
        return timestamps.stream().filter(t -> t.isAfter(cutoff)).count();
    }

    public boolean hasSeenRecipient(long senderId, long recipientId) {
        Set<Long> recipients = recipientsBySender.get(senderId);
        return recipients != null && recipients.contains(recipientId);
    }
}
