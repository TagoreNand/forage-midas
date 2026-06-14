package com.jpmc.midascore.risk;

import java.util.Optional;

/**
 * Strategy (GoF) for one fraud rule. Each scorer inspects a {@link TransferContext} and either
 * fires a {@link FraudSignal} or stays silent. New rules — or an ML-backed scorer (e.g. an ONNX
 * model behind this same interface) — are added as new implementations without touching the
 * evaluator or any other rule (Open/Closed Principle).
 */
public interface FraudScorer {

    Optional<FraudSignal> evaluate(TransferContext context);
}
