package com.jpmc.midascore.adapter.in.messaging;

import com.jpmc.midascore.domain.model.Money;
import com.jpmc.midascore.risk.FraudAssessment;
import com.jpmc.midascore.risk.FraudEvaluationService;
import com.jpmc.midascore.risk.RiskDecision;
import com.jpmc.midascore.risk.TransferContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/**
 * Real-time fraud scoring as an event-driven reaction (Phase 5). It consumes {@code TransferRecorded}
 * events from the domain-events topic, scores each transfer with {@link FraudEvaluationService}, and
 * emits {@code midas.fraud.assessments} metrics; REVIEW/BLOCK outcomes are logged (and, in
 * production, would publish a {@code TransferFlagged} alert / hold settlement).
 *
 * <p>Because it reacts to events rather than sitting in the settlement path, the ledger write path
 * is completely unaffected — the architectural payoff of publishing domain events.
 */
@Component
public class FraudScoringListener {

    private static final Logger log = LoggerFactory.getLogger(FraudScoringListener.class);

    private final FraudEvaluationService fraud;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;

    public FraudScoringListener(FraudEvaluationService fraud, ObjectMapper mapper, MeterRegistry metrics) {
        this.fraud = fraud;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = "${general.events-topic}",
            groupId = "midas-risk",
            containerFactory = "stringKafkaListenerContainerFactory")
    public void onTransferRecorded(String payload) {
        try {
            TransferEvent event = mapper.readValue(payload, TransferEvent.class);
            Money amount = Money.of(
                    BigDecimal.valueOf(event.amountMinor()).movePointLeft(2),
                    Currency.getInstance(event.currency()));
            TransferContext context = new TransferContext(
                    event.senderId(), event.recipientId(), amount, parseTime(event.occurredAt()));

            FraudAssessment assessment = fraud.assess(context);
            metrics.counter("midas.fraud.assessments",
                    "decision", assessment.decision().name().toLowerCase()).increment();

            if (assessment.decision() != RiskDecision.ALLOW) {
                log.warn("Fraud {} for transfer {} (score {}): {}",
                        assessment.decision(), event.transferId(),
                        assessment.totalScore(), assessment.signals());
            }
        } catch (Exception e) {
            log.warn("Fraud scoring skipped for malformed event: {}", e.toString());
        }
    }

    private static Instant parseTime(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    /** Mirror of the outbox TransferRecorded payload on the events topic. */
    record TransferEvent(String transferId, long senderId, long recipientId,
                         long amountMinor, String currency, String occurredAt) {
    }
}
