package com.jpmc.midascore.adapter.in.messaging;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Derives a stable transfer id from a Kafka record's coordinates (topic, partition, offset).
 *
 * <p>The Forage {@code Transaction} carries no business id, yet idempotency needs a key that is
 * identical across redeliveries of the same record. A record's (topic, partition, offset) is
 * exactly that — fixed for the life of the record — so a deterministic UUID derived from it lets
 * the ledger's {@code transferExists} check dedupe redeliveries. When producers begin supplying
 * a real transfer id (Phase 2 schema), prefer that instead.
 */
public final class KafkaTransferId {

    private KafkaTransferId() {
    }

    public static UUID of(String topic, int partition, long offset) {
        String coordinate = topic + "-" + partition + "-" + offset;
        return UUID.nameUUIDFromBytes(coordinate.getBytes(StandardCharsets.UTF_8));
    }
}
