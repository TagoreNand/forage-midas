package com.jpmc.midascore.adapter;

import com.jpmc.midascore.adapter.in.messaging.KafkaTransferId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTransferIdTest {

    @Test
    void same_coordinates_yield_the_same_id_so_redeliveries_dedupe() {
        UUID first = KafkaTransferId.of("midas-transactions", 0, 42L);
        UUID redelivery = KafkaTransferId.of("midas-transactions", 0, 42L);
        assertThat(first).isEqualTo(redelivery);
    }

    @Test
    void different_coordinates_yield_different_ids() {
        UUID base = KafkaTransferId.of("midas-transactions", 0, 42L);
        assertThat(KafkaTransferId.of("midas-transactions", 0, 43L)).isNotEqualTo(base); // offset
        assertThat(KafkaTransferId.of("midas-transactions", 1, 42L)).isNotEqualTo(base); // partition
        assertThat(KafkaTransferId.of("other-topic", 0, 42L)).isNotEqualTo(base);        // topic
    }
}
