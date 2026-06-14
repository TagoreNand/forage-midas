package com.jpmc.midascore.adapter;

import com.jpmc.midascore.adapter.out.event.OutboxEventPublisher;
import com.jpmc.midascore.adapter.out.persistence.OutboxEntity;
import com.jpmc.midascore.adapter.out.persistence.OutboxRepository;
import com.jpmc.midascore.domain.event.TransferRecorded;
import com.jpmc.midascore.domain.model.Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxEventPublisherTest {

    @Test
    void publish_writes_one_unpublished_row_with_a_transport_payload() {
        OutboxRepository repo = mock(OutboxRepository.class);
        OutboxEventPublisher publisher = new OutboxEventPublisher(repo, new ObjectMapper());
        UUID transferId = UUID.randomUUID();

        publisher.publish(TransferRecorded.now(transferId, 1L, 2L, Money.of("30.00", "USD")));

        ArgumentCaptor<OutboxEntity> captor = ArgumentCaptor.forClass(OutboxEntity.class);
        verify(repo).save(captor.capture());
        OutboxEntity row = captor.getValue();

        assertThat(row.getEventType()).isEqualTo("TransferRecorded");
        assertThat(row.getAggregateType()).isEqualTo("Transfer");
        assertThat(row.getAggregateId()).isEqualTo(transferId.toString());
        assertThat(row.isPublished()).isFalse();
        // amount serialised as minor units (precise), not a float
        assertThat(row.getPayload())
                .contains("\"amountMinor\":3000")
                .contains("USD")
                .contains(transferId.toString());
    }
}
