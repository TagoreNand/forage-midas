package com.jpmc.midascore.adapter.in.messaging;

import com.jpmc.midascore.application.port.in.ProcessTransferUseCase;
import com.jpmc.midascore.application.port.in.ProcessTransferUseCase.TransferCommand;
import com.jpmc.midascore.domain.model.Money;
import com.jpmc.midascore.foundation.Transaction;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

/**
 * Inbound messaging adapter - the Kafka consumer the baseline lacked (Finding F4), hardened in
 * Phase 2 with effective idempotency, optimistic-lock retry, and business telemetry.
 *
 * <ul>
 *   <li><b>Idempotency:</b> the transfer id is derived deterministically from the record's
 *       (topic, partition, offset) via {@link KafkaTransferId}, so a Kafka redelivery maps to the
 *       same id and is deduped by the ledger (Finding F5).</li>
 *   <li><b>Concurrency:</b> a lost-update race (Finding F6) surfaces as
 *       {@link ObjectOptimisticLockingFailureException}; the record is retried a bounded number
 *       of times before being allowed to fail to the error handler.</li>
 *   <li><b>Telemetry:</b> every outcome increments a {@code midas.transfers} counter tagged by
 *       result, surfaced via Micrometer/Prometheus.</li>
 *   <li><b>Resilience:</b> on unrecoverable error the record is NOT acked, so the configured
 *       error handler retries and ultimately routes it to the dead-letter topic.</li>
 * </ul>
 */
@Component
public class TransactionKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionKafkaListener.class);
    private static final Currency USD = Currency.getInstance("USD");
    private static final int MAX_LOCK_RETRIES = 3;

    private final ProcessTransferUseCase processTransfer;
    private final MeterRegistry metrics;

    public TransactionKafkaListener(ProcessTransferUseCase processTransfer, MeterRegistry metrics) {
        this.processTransfer = processTransfer;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "${general.kafka-topic}", groupId = "midas-core")
    public void onTransaction(@Payload Transaction tx,
                              @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                              @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                              @Header(KafkaHeaders.OFFSET) long offset,
                              Acknowledgment ack) {
        UUID transferId = KafkaTransferId.of(topic, partition, offset);
        try {
            TransferCommand cmd = new TransferCommand(
                    transferId, tx.getSenderId(), tx.getRecipientId(),
                    Money.of(BigDecimal.valueOf(tx.getAmount()), USD));

            ProcessTransferUseCase.Result result = processWithRetry(cmd);
            metrics.counter("midas.transfers", "result", tag(result)).increment();
            log.info("Transaction {} -> {} amount {} : {}",
                    tx.getSenderId(), tx.getRecipientId(), tx.getAmount(), result);

            ack.acknowledge();
        } catch (Exception ex) {
            metrics.counter("midas.transfers", "result", "error").increment();
            log.error("Processing failed for {}-{}-{}; not acking (error handler -> DLT)",
                    topic, partition, offset, ex);
            throw ex;
        }
    }

    private ProcessTransferUseCase.Result processWithRetry(TransferCommand cmd) {
        int attempt = 0;
        while (true) {
            try {
                return processTransfer.process(cmd);
            } catch (ObjectOptimisticLockingFailureException conflict) {
                if (++attempt >= MAX_LOCK_RETRIES) {
                    throw conflict;
                }
                log.warn("Optimistic-lock conflict on {}, retry {}/{}",
                        cmd.transferId(), attempt, MAX_LOCK_RETRIES);
            }
        }
    }

    private static String tag(ProcessTransferUseCase.Result result) {
        return switch (result) {
            case POSTED -> "posted";
            case DUPLICATE_IGNORED -> "duplicate";
            case REJECTED_INSUFFICIENT_FUNDS -> "rejected_funds";
            case REJECTED_INVALID -> "rejected_invalid";
        };
    }
}
