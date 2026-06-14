package com.jpmc.midascore.domain;

import com.jpmc.midascore.domain.model.Money;
import com.jpmc.midascore.domain.model.ledger.LedgerEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerEntryTest {

    @Test
    void posting_produces_a_balanced_debit_credit_pair() {
        UUID transferId = UUID.randomUUID();
        List<LedgerEntry> posting = LedgerEntry.postingFor(transferId, 1L, 2L, Money.of("30.00", "USD"));

        assertThat(posting).hasSize(2);

        // Conservation of value: the pair sums to exactly zero.
        BigDecimal sum = posting.stream()
                .map(e -> e.amount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum.compareTo(BigDecimal.ZERO)).isZero();

        // Same transfer id groups the pair; one debit (negative) and one credit (positive).
        assertThat(posting).allSatisfy(e -> assertThat(e.transferId()).isEqualTo(transferId));
        assertThat(posting.stream().filter(e -> e.amount().isNegative()).count()).isEqualTo(1);
        assertThat(posting.stream().filter(e -> e.amount().isPositive()).count()).isEqualTo(1);
    }
}
