package com.jpmc.midascore.risk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite fraud evaluator (Phase 5). Runs every registered {@link FraudScorer} strategy over a
 * transfer, sums the fired signals' scores, and maps the total to a {@link RiskDecision} via
 * configurable review/block thresholds. Spring injects all {@code FraudScorer} beans, so adding a
 * rule (or an ML scorer) requires no change here.
 *
 * <p>Scorers read prior history; the transfer is recorded in the {@link FraudFeatureStore}
 * <em>after</em> scoring so a transfer never counts itself in its own velocity/new-recipient check.
 */
@Service
public class FraudEvaluationService {

    private final List<FraudScorer> scorers;
    private final FraudFeatureStore featureStore;
    private final int reviewThreshold;
    private final int blockThreshold;

    public FraudEvaluationService(
            List<FraudScorer> scorers,
            FraudFeatureStore featureStore,
            @Value("${midas.fraud.review-threshold:30}") int reviewThreshold,
            @Value("${midas.fraud.block-threshold:70}") int blockThreshold) {
        this.scorers = scorers;
        this.featureStore = featureStore;
        this.reviewThreshold = reviewThreshold;
        this.blockThreshold = blockThreshold;
    }

    public FraudAssessment assess(TransferContext context) {
        List<FraudSignal> signals = new ArrayList<>();
        for (FraudScorer scorer : scorers) {
            scorer.evaluate(context).ifPresent(signals::add);
        }
        int total = signals.stream().mapToInt(FraudSignal::score).sum();

        featureStore.record(context); // update history after scoring

        RiskDecision decision;
        if (total >= blockThreshold) {
            decision = RiskDecision.BLOCK;
        } else if (total >= reviewThreshold) {
            decision = RiskDecision.REVIEW;
        } else {
            decision = RiskDecision.ALLOW;
        }
        return new FraudAssessment(decision, total, signals);
    }
}
