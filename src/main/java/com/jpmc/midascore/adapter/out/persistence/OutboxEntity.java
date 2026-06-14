package com.jpmc.midascore.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox row (ARCHITECTURE.md section 8). A domain event is inserted here in the
 * SAME database transaction as the ledger postings, so the event and the money movement commit
 * atomically - eliminating the dual-write problem (event sent but DB rolled back, or vice versa).
 * A relay later publishes unpublished rows to Kafka. The index on {@code published} keeps the
 * relay's scan cheap.
 *
 * <p>The payload is a sized varchar (not {@code @Lob}) so it maps cleanly on both H2 and
 * PostgreSQL and avoids the well-known Hibernate {@code @Lob String} -> large-object pitfall.
 */
@Entity
@Table(name = "outbox", indexes = {
        @Index(name = "idx_outbox_unpublished", columnList = "published, occurred_at")
})
public class OutboxEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, length = 2000)
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "published", nullable = false)
    private boolean published;

    protected OutboxEntity() {
    }

    public OutboxEntity(UUID id, String aggregateType, String aggregateId,
                        String eventType, String payload, Instant occurredAt, boolean published) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.published = published;
    }

    public void markPublished() {
        this.published = true;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public boolean isPublished() {
        return published;
    }
}
