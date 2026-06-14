package com.jpmc.midascore.application.port.out;

import com.jpmc.midascore.domain.model.Money;
import com.jpmc.midascore.domain.model.Transfer;

/**
 * Driven port for the external incentive/rewards service.
 *
 * <p>The application depends on this interface, NOT on HTTP or the
 * {@code transaction-incentive-api.jar}. That is what lets the outbound adapter wrap the
 * call in a Resilience4j circuit breaker (Roadmap §2.3) and return a graceful zero-reward
 * fallback during an outage without the core settlement logic ever knowing.
 */
public interface IncentivePort {

    /** Returns the reward for a settled transfer, or {@code Money.zero} if unavailable. */
    Money rewardFor(Transfer transfer);
}
