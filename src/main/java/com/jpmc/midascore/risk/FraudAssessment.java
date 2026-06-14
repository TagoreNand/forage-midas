package com.jpmc.midascore.risk;

import java.util.List;

/**
 * The aggregate result of scoring one transfer: the decision, the total score, and the list of
 * signals that fired (kept for explainability, alerting, and training-data feedback).
 */
public record FraudAssessment(RiskDecision decision, int totalScore, List<FraudSignal> signals) {
}
