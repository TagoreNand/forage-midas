package com.jpmc.midascore.batch;

import com.jpmc.midascore.adapter.out.persistence.LedgerEntryRepository;
import com.jpmc.midascore.batch.ReconciliationService.ReconciliationResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReconciliationServiceTest {

    private final LedgerEntryRepository ledger = mock(LedgerEntryRepository.class);
    private final ReconciliationService service = new ReconciliationService(ledger, new SimpleMeterRegistry());

    @Test
    void a_balanced_ledger_reconciles() {
        when(ledger.totalMinor()).thenReturn(0L);

        ReconciliationResult result = service.reconcile();

        assertThat(result.balanced()).isTrue();
        assertThat(result.ledgerSumMinor()).isZero();
    }

    @Test
    void a_nonzero_sum_is_flagged_as_a_mismatch() {
        when(ledger.totalMinor()).thenReturn(1500L);

        ReconciliationResult result = service.reconcile();

        assertThat(result.balanced()).isFalse();
        assertThat(result.ledgerSumMinor()).isEqualTo(1500L);
    }
}
