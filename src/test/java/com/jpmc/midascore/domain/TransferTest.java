package com.jpmc.midascore.domain;

import com.jpmc.midascore.domain.model.Money;
import com.jpmc.midascore.domain.model.Transfer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferTest {

    @Test
    void valid_transfer_constructs() {
        Transfer t = new Transfer(UUID.randomUUID(), 1L, 2L, Money.of("10.00", "USD"));
        assertThat(t.senderId()).isEqualTo(1L);
        assertThat(t.recipientId()).isEqualTo(2L);
    }

    @Test
    void self_transfer_is_rejected() {
        assertThatThrownBy(() -> new Transfer(UUID.randomUUID(), 1L, 1L, Money.of("10.00", "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void non_positive_amount_is_rejected() {
        assertThatThrownBy(() -> new Transfer(UUID.randomUUID(), 1L, 2L, Money.of("0.00", "USD")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Transfer(UUID.randomUUID(), 1L, 2L, Money.of("-1.00", "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
