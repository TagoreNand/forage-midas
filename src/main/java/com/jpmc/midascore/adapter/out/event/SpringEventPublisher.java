package com.jpmc.midascore.adapter.out.event;

import com.jpmc.midascore.application.port.out.EventPublisherPort;
import com.jpmc.midascore.domain.event.TransferRecorded;
import org.springframework.context.ApplicationEventPublisher;

/**
 * In-process (non-durable) event publisher. Superseded in Phase 2 by {@link OutboxEventPublisher}
 * (transactional outbox), which is the wired {@code @Component}. Retained as a reference
 * alternative and intentionally NOT a Spring bean, so exactly one EventPublisherPort is active.
 */
public class SpringEventPublisher implements EventPublisherPort {

    private final ApplicationEventPublisher publisher;

    public SpringEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(TransferRecorded event) {
        publisher.publishEvent(event);
    }
}
