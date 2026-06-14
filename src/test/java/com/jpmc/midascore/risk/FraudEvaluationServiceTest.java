package com.jpmc.midascore.risk;

import com.jpmc.midascore.domain.model.Money;
import com.jpmc.midascore.risk.scorer.LargeAmountScorer;
import com.jpmc.midascore.risk.scorer.NewRecipientScorer;
import com.jpmc.midascore.risk.scorer.VelocityScorer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Strategy + composite scoring with real scorers and the in-memory feature store
 * (no Spring). Thresholds: review >= 30, block >= 70.
 */
class FraudEvaluationServiceTest {

    private final FraudFeatureStore store = new FraudFeatureStore();
    private final FraudEvaluationService service = new FraudEvaluationService(
            List.of(
                    new LargeAmountScorer(new BigDecimal("10000"), 50),
                    new VelocityScorer(store, 3, 60, 40),
                    new NewRecipientScorer(store, 20)),
            store, 30, 70);

    private static TransferContext ctx(long sender, long recipient, String amount, Instant at) {
        return new TransferContext(sender, recipient, Money.of(amount, "USD"), at);
    }

    @Test
    void small_transfer_to_a_known_recipient_is_allowed() {
        Instant t = Instant.now();
        service.assess(ctx(1, 2, "100.00", t));                 // first time: NEW_RECIPIENT(20) -> ALLOW
        FraudAssessment second = service.assess(ctx(1, 2, "100.00", t));

        assertThat(second.decision()).isEqualTo(RiskDecision.ALLOW);
        assertThat(second.totalScore()).isZero();
    }

    @Test
    void large_amount_to_a_new_recipient_is_blocked() {
        FraudAssessment assessment = service.assess(ctx(1, 9, "20000.00", Instant.now()));

        assertThat(assessment.decision()).isEqualTo(RiskDecision.BLOCK); // 50 + 20 = 70
        assertThat(assessment.signals()).extracting(FraudSignal::rule)
                .contains("LARGE_AMOUNT", "NEW_RECIPIENT");
    }

    @Test
    void rapid_repeated_transfers_trigger_review() {
        Instant t = Instant.now();
        service.assess(ctx(5, 6, "10.00", t));
        service.assess(ctx(5, 6, "10.00", t));
        service.assess(ctx(5, 6, "10.00", t));
        FraudAssessment fourth = service.assess(ctx(5, 6, "10.00", t)); // velocity 3 >= 3 -> 40

        assertThat(fourth.decision()).isEqualTo(RiskDecision.REVIEW);
        assertThat(fourth.signals()).extracting(FraudSignal::rule).contains("VELOCITY");
    }
}
