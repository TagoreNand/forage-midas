package com.jpmc.midascore.risk;

/**
 * A single fired risk indicator: which rule, how many points it contributes to the total risk
 * score, and a human-readable reason (used for explainability and audit).
 */
public record FraudSignal(String rule, int score, String reason) {
}
