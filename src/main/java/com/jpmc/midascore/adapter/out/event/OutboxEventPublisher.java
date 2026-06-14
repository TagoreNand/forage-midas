package com.jpmc.midascore.adapter.out.event;

import com.jpmc.midascore.adapter.out.persistence.OutboxEntity;
import com.jpmc.midascore.adapter.out.persistence.OutboxRepository;
import com.jpmc.midascore.application.port.out.EventPublisherPort;
import com.jpmc.midascore.domain.event.TransferRecorded;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound event adapter implementing {@link EventPublisherPort} via the transactional outbox.
 *
 * <p>Because {@code ProcessTransferService#process} is {@code @Transactional}, this insert
 * commits atomically with the ledger postings and balance update — the event can never be lost
 * or emitted for a transfer that rolled back. {@code OutboxRelay} forwards rows to Kafka.
 *
 * <p>The event is serialised to a flat, transport-stable payload (amount as minor units) rather
 * than the in-memory {@code Money} object, so the wire contract is explicit and Jackson-friendly.
 */
@Component
public class OutboxEventPublisher implements EventPublisherPort {

    private final OutboxRepository outbox;
    private final ObjectMapper mapper;

    public OutboxEventPublisher(OutboxRepository outbox, ObjectMapper mapper) {
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Override
    public void publish(TransferRecorded event) {
        try {
            String json = mapper.writeValueAsString(new Payload(
                    event.transferId().toString(),
                    event.senderId(),
                    event.recipientId(),
                    event.amount().amount().movePointRight(2).setScale(0).longValueExact(),
                    event.amount().currency().getCurrencyCode(),
                    event.occurredAt().toString()));

            outbox.save(new OutboxEntity(
                    UUID.randomUUID(), "Transfer", event.transferId().toString(),
                    "TransferRecorded", json, event.occurredAt() != null ? event.occurredAt() : Instant.now(),
                    false));
        } catch (JsonProcessingException e) {
            // Same transaction as the postings: failing here rolls the whole transfer back.
            throw new IllegalStateException("Failed to serialise outbox payload", e);
        }
    }

    /** Transport contract for the TransferRecorded event. */
    public record Payload(String transferId, long senderId, long recipientId,
                          long amountMinor, String currency, String occurredAt) {
    }
}
