package com.jpmc.midascore.application.port.out;

import com.jpmc.midascore.domain.model.Money;

import java.util.Optional;

/**
 * Outbound port for the balance read-model cache (CQRS query side, Phase 3).
 *
 * <p>Implementations MUST degrade gracefully: the cache is a disposable accelerator, never a
 * source of truth. A backing-store outage surfaces as a miss ({@code get} returns empty) or a
 * silent no-op ({@code put}/{@code evict}), so reads always fall back to the database and
 * correctness is preserved.
 */
public interface BalanceCachePort {

    Optional<Money> get(long accountId);

    void put(long accountId, Money balance);

    void evict(long accountId);
}
