package com.jpmc.midascore.application.service;

import com.jpmc.midascore.application.port.in.GetBalanceUseCase;
import com.jpmc.midascore.application.port.out.AccountRepositoryPort;
import com.jpmc.midascore.application.port.out.BalanceCachePort;
import com.jpmc.midascore.domain.model.Account;
import com.jpmc.midascore.domain.model.Money;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.Currency;
import java.util.Optional;

/**
 * CQRS read service for balances using the <strong>cache-aside</strong> pattern (Phase 3):
 * consult the cache first; on a miss, load from the source of truth and populate the cache.
 * Hit/miss ratios are exported as {@code midas.balance.cache} metrics. Because the cache
 * degrades gracefully, a Redis outage simply turns every read into a miss + DB load.
 */
@Service
public class BalanceQueryService implements GetBalanceUseCase {

    private static final Currency USD = Currency.getInstance("USD");

    private final AccountRepositoryPort accounts;
    private final BalanceCachePort cache;
    private final MeterRegistry metrics;

    public BalanceQueryService(AccountRepositoryPort accounts,
                               BalanceCachePort cache,
                               MeterRegistry metrics) {
        this.accounts = accounts;
        this.cache = cache;
        this.metrics = metrics;
    }

    @Override
    public Money balanceOf(long accountId) {
        Optional<Money> cached = cache.get(accountId);
        if (cached.isPresent()) {
            metrics.counter("midas.balance.cache", "result", "hit").increment();
            return cached.get();
        }
        metrics.counter("midas.balance.cache", "result", "miss").increment();

        Money balance = accounts.findById(accountId)
                .map(Account::balance)
                .orElse(Money.zero(USD));
        cache.put(accountId, balance);
        return balance;
    }
}
