package com.jpmc.midascore.adapter.out.cache;

import com.jpmc.midascore.application.port.out.BalanceCachePort;
import com.jpmc.midascore.domain.model.Money;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default {@link BalanceCachePort} — a no-op used when {@code midas.cache.enabled} is false or
 * absent. Every read is a miss (so the query service goes to the database) and writes do nothing.
 * This lets the application build and run with no Redis at all; the Redis adapter is opt-in.
 */
@Component
@ConditionalOnProperty(prefix = "midas.cache", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpBalanceCache implements BalanceCachePort {

    @Override
    public Optional<Money> get(long accountId) {
        return Optional.empty();
    }

    @Override
    public void put(long accountId, Money balance) {
        // no-op
    }

    @Override
    public void evict(long accountId) {
        // no-op
    }
}
