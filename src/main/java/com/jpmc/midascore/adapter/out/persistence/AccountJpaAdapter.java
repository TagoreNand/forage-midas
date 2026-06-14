package com.jpmc.midascore.adapter.out.persistence;

import com.jpmc.midascore.application.port.out.AccountRepositoryPort;
import com.jpmc.midascore.application.port.out.BalanceCachePort;
import com.jpmc.midascore.domain.model.Account;
import com.jpmc.midascore.domain.model.Money;
import com.jpmc.midascore.domain.model.ledger.LedgerEntry;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound persistence adapter - the single place that knows about JPA, implementing
 * {@link AccountRepositoryPort}. Replaces the leaky {@code DatabaseConduit} (Finding F12).
 *
 * <p>Phase-1 bridge: the legacy {@link UserRecord} (seeded by the Forage test harness via
 * {@code DatabaseConduit}) remains the account balance store, while the precise
 * {@code LedgerEntryEntity} records every posting in minor units. The adapter maps between the
 * legacy {@code float} column and the domain {@link Money}, and carries the {@code @Version}
 * token for optimistic locking (Phase 2, Finding F6).
 *
 * <p>Phase 3: after persisting balances it writes them through to the {@link BalanceCachePort}
 * (the read-model cache), so a subsequent {@code GET /balance} never serves a stale value after
 * a settled transfer. The cache degrades gracefully, so this is a no-op when caching is disabled.
 */
@Component
public class AccountJpaAdapter implements AccountRepositoryPort {

    private static final Currency USD = Currency.getInstance("USD");

    private final UserRepository users;
    private final LedgerEntryRepository ledger;
    private final BalanceCachePort cache;

    public AccountJpaAdapter(UserRepository users, LedgerEntryRepository ledger, BalanceCachePort cache) {
        this.users = users;
        this.ledger = ledger;
        this.cache = cache;
    }

    @Override
    public Optional<Account> findById(long id) {
        UserRecord record = users.findById(id);
        if (record == null) {
            return Optional.empty();
        }
        Money balance = Money.of(BigDecimal.valueOf(record.getBalance()), USD);
        return Optional.of(new Account(record.getId(), record.getName(), balance, record.getVersion()));
    }

    @Override
    public void saveAll(List<Account> accounts) {
        for (Account account : accounts) {
            UserRecord record = users.findById(account.id());
            if (record != null) {
                record.setBalance(account.balance().amount().floatValue());
                users.save(record);
                cache.put(account.id(), account.balance()); // write-through: keep the read model coherent
            }
        }
    }

    @Override
    public void appendLedger(List<LedgerEntry> entries) {
        List<LedgerEntryEntity> rows = new ArrayList<>(entries.size());
        for (LedgerEntry e : entries) {
            long minor = e.amount().amount().movePointRight(2).setScale(0).longValueExact();
            rows.add(new LedgerEntryEntity(
                    e.id(), e.accountId(), minor, e.transferId(),
                    e.amount().currency().getCurrencyCode(), e.createdAt()));
        }
        ledger.saveAll(rows);
    }

    @Override
    public boolean transferExists(UUID transferId) {
        return ledger.existsByTransferId(transferId);
    }
}
