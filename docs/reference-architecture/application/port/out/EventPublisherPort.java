package com.jpmc.midascore.application.port.out;

import com.jpmc.midascore.domain.event.TransferRecorded;

/**
 * Driven port for publishing domain events.
 *
 * <p>The production implementation writes to the transactional <em>outbox</em> in the same
 * DB transaction as the ledger postings (ARCHITECTURE.md §8), so an event is never lost or
 * double-emitted relative to the money movement. A relay later forwards outbox rows to Kafka.
 */
public interface EventPublisherPort {

    void publish(TransferRecorded event);
}
