package com.jpmc.midascore.risk;

/**
 * The outcome of fraud evaluation, derived from the aggregate risk score:
 * {@code ALLOW} (clean), {@code REVIEW} (flag for a human / step-up), {@code BLOCK} (hold).
 */
public enum RiskDecision {
    ALLOW,
    REVIEW,
    BLOCK
}
