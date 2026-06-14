package com.jpmc.midascore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka resilience + scheduling configuration (Phase 2).
 *
 * <p>The {@link DefaultErrorHandler} retries a failing record a bounded number of times, then
 * the {@link DeadLetterPublishingRecoverer} routes it to {@code <topic>.DLT}. This prevents a
 * single poison message from blocking the partition (head-of-line blocking) while guaranteeing
 * no transaction is silently dropped — it lands in the dead-letter topic for triage. Spring Boot
 * auto-wires this error handler into the Kafka listener containers.
 *
 * <p>{@code @EnableScheduling} activates the {@code OutboxRelay} poll loop.
 */
@Configuration
@EnableScheduling
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        // 3 total attempts (initial + 2 retries), 1s apart, then publish to the DLT.
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    }
}
