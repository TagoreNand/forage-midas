package com.jpmc.midascore.adapter.out.event;

import com.jpmc.midascore.adapter.out.persistence.OutboxEntity;
import com.jpmc.midascore.adapter.out.persistence.OutboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Polling relay that forwards committed outbox rows to Kafka (the second half of the
 * transactional-outbox pattern). Runs on a schedule, drains the oldest unpublished rows in
 * bounded batches, publishes each to the events topic, and marks it published on success.
 *
 * <p>On a publish failure it stops the batch and retries next tick, so a down broker is handled
 * gracefully and event ordering per poll is preserved. Production would graduate this to
 * Debezium CDC; the application contract (rows land in the outbox atomically) is identical.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper mapper;
    private final String eventsTopic;

    public OutboxRelay(OutboxRepository outbox,
                       KafkaTemplate<String, Object> kafka,
                       ObjectMapper mapper,
                       @Value("${general.events-topic}") String eventsTopic) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.mapper = mapper;
        this.eventsTopic = eventsTopic;
    }

    @Scheduled(fixedDelayString = "${midas.outbox.relay-delay-ms:5000}")
    public void drain() {
        List<OutboxEntity> batch = outbox.findByPublishedFalseOrderByOccurredAtAsc(Limit.of(BATCH_SIZE));
        int published = 0;
        for (OutboxEntity row : batch) {
            try {
                JsonNode payload = mapper.readTree(row.getPayload());
                kafka.send(eventsTopic, row.getAggregateId(), payload).get();
                row.markPublished();
                outbox.save(row);
                published++;
            } catch (Exception e) {
                log.warn("Outbox relay: could not publish {} ({}); retrying next tick",
                        row.getId(), e.toString());
                break; // preserve ordering; don't hammer a degraded broker
            }
        }
        if (published > 0) {
            log.debug("Outbox relay published {} event(s)", published);
        }
    }
}
