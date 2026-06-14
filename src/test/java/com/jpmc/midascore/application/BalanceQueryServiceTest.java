package com.jpmc.midascore.application;

import com.jpmc.midascore.application.port.out.AccountRepositoryPort;
import com.jpmc.midascore.application.port.out.BalanceCachePort;
import com.jpmc.midascore.application.service.BalanceQueryService;
import com.jpmc.midascore.domain.model.Account;
import com.jpmc.midascore.domain.model.Money;
import com.jpmc.midascore.domain.model.ledger.LedgerEntry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the cache-aside read service (no Spring, no Redis) using in-memory fakes.
 */
class BalanceQueryServiceTest {

    private static final Currency USD = Currency.getInstance("USD");

    private final FakeRepo repo = new FakeRepo();
    private final FakeCache cache = new FakeCache();
    private final BalanceQueryService service =
            new BalanceQueryService(repo, cache, new SimpleMeterRegistry());

    @Test
    void miss_loads_from_db_and_populates_the_cache() {
        repo.put(new Account(1L, "alice", Money.of("70.00", "USD"), 0L));

        Money result = service.balanceOf(1);

        assertThat(result).isEqualTo(Money.of("70.00", "USD"));
        assertThat(cache.store).containsKey(1L); // populated on the miss
    }

    @Test
    void hit_is_served_from_cache_without_consulting_the_database() {
        cache.store.put(2L, Money.of("123.45", "USD"));
        repo.put(new Account(2L, "bob", Money.of("999.99", "USD"), 0L)); // DB differs on purpose

        Money result = service.balanceOf(2);

        assertThat(result).isEqualTo(Money.of("123.45", "USD")); // cached value wins
    }

    @Test
    void unknown_account_returns_zero() {
        assertThat(service.balanceOf(404)).isEqualTo(Money.zero(USD));
    }

    private static final class FakeCache implements BalanceCachePort {
        private final Map<Long, Money> store = new HashMap<>();

        @Override
        public Optional<Money> get(long accountId) {
            return Optional.ofNullable(store.get(accountId));
        }

        @Override
        public void put(long accountId, Money balance) {
            store.put(accountId, balance);
        }

        @Override
        public void evict(long accountId) {
            store.remove(accountId);
        }
    }

    private static final class FakeRepo implements AccountRepositoryPort {
        private final Map<Long, Account> accounts = new HashMap<>();

        void put(Account a) {
            accounts.put(a.id(), a);
        }

        @Override
        public Optional<Account> findById(long id) {
            return Optional.ofNullable(accounts.get(id));
        }

        @Override
        public void saveAll(List<Account> toSave) {
        }

        @Override
        public void appendLedger(List<LedgerEntry> entries) {
        }

        @Override
        public boolean transferExists(UUID transferId) {
            return false;
        }
    }
}
