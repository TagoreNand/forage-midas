package com.jpmc.midascore.adapter.out.persistence;

import com.jpmc.midascore.application.port.out.AccountRepositoryPort;
import com.jpmc.midascore.domain.model.Account;
import com.jpmc.midascore.domain.model.ledger.LedgerEntry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven (outbound) persistence adapter — implements {@link AccountRepositoryPort} and is
 * the single place that knows about JPA. The domain depends on the port; this class depends
 * on the domain. That inversion replaces the leaky {@code DatabaseConduit} (Finding F12).
 *
 * <p>It maps between the framework-free domain {@link Account} and the JPA {@code @Entity}
 * (which carries the {@code @Version} column for optimistic locking, Finding F6) so JPA
 * concerns never leak inward. Two Spring Data repositories — {@code AccountJpaRepository}
 * and {@code LedgerEntryJpaRepository} — are collaborators (omitted here for brevity).
 */
@Component
public class AccountJpaAdapter implements AccountRepositoryPort {

    private final AccountJpaRepository accountRepo;
    private final LedgerEntryJpaRepository ledgerRepo;
    private final AccountMapper mapper;

    public AccountJpaAdapter(AccountJpaRepository accountRepo,
                             LedgerEntryJpaRepository ledgerRepo,
                             AccountMapper mapper) {
        this.accountRepo = accountRepo;
        this.ledgerRepo = ledgerRepo;
        this.mapper = mapper;
    }

    @Override
    public Optional<Account> findById(long id) {
        return accountRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    public void saveAll(List<Account> accounts) {
        // Hibernate raises OptimisticLockException on a version conflict; the caller retries.
        accountRepo.saveAll(accounts.stream().map(mapper::toEntity).toList());
    }

    @Override
    public void appendLedger(List<LedgerEntry> entries) {
        // Append-only: insert, never update (the ledger is immutable). (Finding F3)
        ledgerRepo.saveAll(entries.stream().map(mapper::toEntity).toList());
    }

    @Override
    public boolean transferExists(UUID transferId) {
        // Durable idempotency backstop; the PK constraint also prevents a duplicate insert.
        return ledgerRepo.existsByTransferId(transferId);
    }

    // --- Collaborators (interfaces/mappers) referenced above; shown as signatures only. ---
    interface AccountJpaRepository extends org.springframework.data.repository.CrudRepository<AccountEntity, Long> {}
    interface LedgerEntryJpaRepository extends org.springframework.data.repository.CrudRepository<LedgerEntryEntity, UUID> {
        boolean existsByTransferId(UUID transferId);
    }
    interface AccountMapper {
        Account toDomain(AccountEntity e);
        AccountEntity toEntity(Account a);
        LedgerEntryEntity toEntity(LedgerEntry l);
    }
    static final class AccountEntity {}      // @Entity with @Version (mapping omitted)
    static final class LedgerEntryEntity {}  // @Entity, append-only (mapping omitted)
}
