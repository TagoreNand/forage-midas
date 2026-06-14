package com.jpmc.midascore.risk.scorer;

import com.jpmc.midascore.risk.FraudFeatureStore;
import com.jpmc.midascore.risk.FraudScorer;
import com.jpmc.midascore.risk.FraudSignal;
import com.jpmc.midascore.risk.TransferContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** Fires when a sender exceeds an allowed number of transfers within a sliding time window. */
@Component
public class VelocityScorer implements FraudScorer {

    private final FraudFeatureStore featureStore;
    private final int maxPerWindow;
    private final Duration window;
    private final int score;

    public VelocityScorer(
            FraudFeatureStore featureStore,
            @Value("${midas.fraud.velocity-max:5}") int maxPerWindow,
            @Value("${midas.fraud.velocity-window-seconds:60}") long windowSeconds,
            @Value("${midas.fraud.velocity-score:40}") int score) {
        this.featureStore = featureStore;
        this.maxPerWindow = maxPerWindow;
        this.window = Duration.ofSeconds(windowSeconds);
        this.score = score;
    }

    @Override
    public Optional<FraudSignal> evaluate(TransferContext context) {
        long recent = featureStore.recentCount(context.senderId(), window, context.occurredAt());
        if (recent >= maxPerWindow) {
            return Optional.of(new FraudSignal(
                    "VELOCITY", score,
                    "sender " + context.senderId() + " made " + recent
                            + " transfers within " + window.toSeconds() + "s"));
        }
        return Optional.empty();
    }
}
