package com.jpmc.midascore.risk.scorer;

import com.jpmc.midascore.risk.FraudScorer;
import com.jpmc.midascore.risk.FraudSignal;
import com.jpmc.midascore.risk.TransferContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/** Fires when a transfer's amount meets or exceeds a configured large-value threshold. */
@Component
public class LargeAmountScorer implements FraudScorer {

    private final BigDecimal threshold;
    private final int score;

    public LargeAmountScorer(
            @Value("${midas.fraud.large-amount-threshold:10000}") BigDecimal threshold,
            @Value("${midas.fraud.large-amount-score:50}") int score) {
        this.threshold = threshold;
        this.score = score;
    }

    @Override
    public Optional<FraudSignal> evaluate(TransferContext context) {
        if (context.amount().amount().compareTo(threshold) >= 0) {
            return Optional.of(new FraudSignal(
                    "LARGE_AMOUNT", score, "amount " + context.amount() + " >= " + threshold));
        }
        return Optional.empty();
    }
}
