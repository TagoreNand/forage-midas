package com.jpmc.midascore.risk.scorer;

import com.jpmc.midascore.risk.FraudFeatureStore;
import com.jpmc.midascore.risk.FraudScorer;
import com.jpmc.midascore.risk.FraudSignal;
import com.jpmc.midascore.risk.TransferContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Fires the first time a sender pays a given recipient (a common fraud / account-takeover tell). */
@Component
public class NewRecipientScorer implements FraudScorer {

    private final FraudFeatureStore featureStore;
    private final int score;

    public NewRecipientScorer(
            FraudFeatureStore featureStore,
            @Value("${midas.fraud.new-recipient-score:20}") int score) {
        this.featureStore = featureStore;
        this.score = score;
    }

    @Override
    public Optional<FraudSignal> evaluate(TransferContext context) {
        if (!featureStore.hasSeenRecipient(context.senderId(), context.recipientId())) {
            return Optional.of(new FraudSignal(
                    "NEW_RECIPIENT", score,
                    "first transfer from " + context.senderId() + " to " + context.recipientId()));
        }
        return Optional.empty();
    }
}
