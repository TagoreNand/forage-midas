package com.jpmc.midascore.adapter.in.messaging;

import com.jpmc.midascore.application.port.in.ProcessTransferUseCase;
import com.jpmc.midascore.application.port.in.ProcessTransferUseCase.TransferCommand;
import com.jpmc.midascore.domain.model.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Currency;
import java.util.UUID;

/**
 * Driving (inbound) adapter — resolves Finding F4 (no Kafka consumer existed in main).
 *
 * <p>It is intentionally thin: deserialise, map to a command, delegate to the use-case port,
 * and acknowledge. It holds <em>no business logic</em> (SOLID's S) and depends on the port,
 * not the concrete service (D). Manual acknowledgement commits the offset only after the
 * use case's transaction succeeds, which (with the idempotency key) gives exactly-once
 * <em>effect</em> over Kafka's at-least-once delivery (ARCHITECTURE.md §8).
 */
@Component
public class TransactionKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionKafkaListener.class);
    private static final Currency USD = Currency.getInstance("USD");

    private final ProcessTransferUseCase processTransfer;

    public TransactionKafkaListener(ProcessTransferUseCase processTransfer) {
        this.processTransfer = processTransfer;
    }

    @KafkaListener(topics = "${general.kafka-topic}", groupId = "midas-core")
    public void onTransaction(@Payload IncomingTransaction msg, Acknowledgment ack) {
        try {
            TransferCommand cmd = new TransferCommand(
                    msg.idOrNew(),
                    msg.senderId(),
                    msg.recipientId(),
                    Money.of(msg.amount(), USD));

            ProcessTransferUseCase.Result result = processTransfer.process(cmd);
            log.info("Transfer {} -> {} amount {} result {}",
                    msg.senderId(), msg.recipientId(), msg.amount(), result);

            ack.acknowledge(); // commit offset only after successful processing
        } catch (Exception ex) {
            // Do NOT ack: let the error handler retry, then route to the dead-letter topic
            // (configured via DefaultErrorHandler + DeadLetterPublishingRecoverer) so a poison
            // message never blocks the partition (head-of-line blocking). (§2.1)
            log.error("Failed to process transaction; routing to DLT", ex);
            throw ex;
        }
    }

    /** Transport DTO. A real deployment enforces this shape with an Avro schema (§2.1). */
    public record IncomingTransaction(UUID id, long senderId, long recipientId, String amount) {
        UUID idOrNew() {
            return id != null ? id : UUID.randomUUID();
        }
    }
}
