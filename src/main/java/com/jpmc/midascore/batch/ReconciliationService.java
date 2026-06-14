package com.jpmc.midascore.batch;

import com.jpmc.midascore.adapter.out.persistence.LedgerEntryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled financial control (Phase 5): proves the double-entry ledger still conserves value.
 *
 * <p>Every signed ledger entry should sum to exactly zero; any non-zero total means money was
 * created or destroyed — a serious integrity breach. The job runs on a schedule, records a
 * {@code midas.reconciliation} metric, and logs loudly on mismatch so alerting can page on-call.
 * This turns "is our ledger corrupted?" from a latent risk into a continuously-verified invariant.
 */
@Component
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final LedgerEntryRepository ledger;
    private final MeterRegistry metrics;

    public ReconciliationService(LedgerEntryRepository ledger, MeterRegistry metrics) {
        this.ledger = ledger;
        this.metrics = metrics;
    }

    /** Defaults to hourly (6-field Spring cron); override via {@code midas.reconciliation.cron}. */
    @Scheduled(cron = "${midas.reconciliation.cron:0 0 * * * *}")
    public ReconciliationResult reconcile() {
        long total = ledger.totalMinor();
        boolean balanced = total == 0L;

        metrics.counter("midas.reconciliation", "result", balanced ? "balanced" : "mismatch").increment();
        if (balanced) {
            log.info("Reconciliation OK: ledger conserves value (sum = 0)");
        } else {
            log.error("RECONCILIATION MISMATCH: ledger sum = {} minor units (expected 0) - investigate now",
                    total);
        }
        return new ReconciliationResult(total, balanced);
    }

    public record ReconciliationResult(long ledgerSumMinor, boolean balanced) {
    }
}
