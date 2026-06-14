package com.jpmc.midascore.adapter.out.persistence;

import org.springframework.data.domain.Limit;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the transactional outbox. The relay drains the oldest unpublished rows in
 * batches; {@code Limit} caps each poll so one tick never loads the whole backlog.
 */
public interface OutboxRepository extends CrudRepository<OutboxEntity, UUID> {

    List<OutboxEntity> findByPublishedFalseOrderByOccurredAtAsc(Limit limit);
}
