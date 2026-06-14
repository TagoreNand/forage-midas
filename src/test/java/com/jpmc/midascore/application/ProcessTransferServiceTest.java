package com.jpmc.midascore.application;

import com.jpmc.midascore.application.port.in.ProcessTransferUseCase.Result;
import com.jpmc.midascore.application.port.in.ProcessTransferUseCase.TransferCommand;
import com.jpmc.midascore.application.port.out.AccountRepositoryPort;
import com.jpmc.midascore.application.port.out.EventPublisherPort;
import com.jpmc.midascore.application.port.out.IncentivePort;
import com.jpmc.midascore.application.service.ProcessTransferService;
import com.jpmc.midascore.domain.event.TransferRecorded;
import com.jpmc.midascore.domain.model.Account;
import com.jpmc.midascore.domain.model.Money;
import com.jpmc.midascore.domain.model.Transfer;
import com.jpmc.midascore.domain.model.ledger.LedgerEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the orchestration logic, using in-memory port fakes (no Spring, no DB,
 * no Kafka). Fast, deterministic, and terminating — unlike the interactive Forage verifiers.
 */
class ProcessTransferServiceTest {

    private static final Currency USD = Currency.getInstance("USD");

    private final FakeAccountRepository repo = new FakeAccountRepository();
    private final FakeEvents events = new FakeEvents();

    private ProcessTransferService service(IncentivePort incentives) {
        return new ProcessTransferService(repo, incentives, events);
    }

    private static Account account(long id, String balance) {
        return new Account(id, "user-" + id, Money.of(balance, "USD"), 0L);
    }

    private static TransferCommand command(UUID id, long sender, long recipient, String amount) {
        return new TransferCommand(id, sender, recipient, Money.of(amount, "USD"));
    }

    @Test
    void happy_path_moves_money_and_balances_the_ledger() {
        repo.seed(account(1, "100.00"));
        repo.seed(account(2, "50.00"));

        Result result = service(noReward()).process(command(UUID.randomUUID(), 1, 2, "30.00"));

        assertThat(result).isEqualTo(Result.POSTED);
        assertThat(repo.balanceOf(1)).isEqualTo(Money.of("70.00", "USD"));
        assertThat(repo.balanceOf(2)).isEqualTo(Money.of("80.00", "USD"));
        assertThat(repo.ledgerSum().compareTo(BigDecimal.ZERO)).isZero();
        assertThat(events.published).hasSize(1);
    }

    @Test
    void insufficient_funds_is_rejected_with_no_side_effects() {
        repo.seed(account(1, "10.00"));
        repo.seed(account(2, "50.00"));

        Result result = service(noReward()).process(command(UUID.randomUUID(), 1, 2, "30.00"));

        assertThat(result).isEqualTo(Result.REJECTED_INSUFFICIENT_FUNDS);
        assertThat(repo.balanceOf(1)).isEqualTo(Money.of("10.00", "USD"));
        assertThat(repo.balanceOf(2)).isEqualTo(Money.of("50.00", "USD"));
        assertThat(repo.ledger).isEmpty();
        assertThat(events.published).isEmpty();
    }

    @Test
    void unknown_recipient_is_rejected() {
        repo.seed(account(1, "100.00")); // no account 2

        Result result = service(noReward()).process(command(UUID.randomUUID(), 1, 2, "30.00"));

        assertThat(result).isEqualTo(Result.REJECTED_INVALID);
    }

    @Test
    void self_transfer_is_rejected() {
        repo.seed(account(1, "100.00"));

        Result result = service(noReward()).process(command(UUID.randomUUID(), 1, 1, "10.00"));

        assertThat(result).isEqualTo(Result.REJECTED_INVALID);
    }

    @Test
    void duplicate_delivery_is_ignored() {
        repo.seed(account(1, "100.00"));
        repo.seed(account(2, "50.00"));
        UUID transferId = UUID.randomUUID();
        ProcessTransferService service = service(noReward());

        service.process(command(transferId, 1, 2, "30.00"));
        Result second = service.process(command(transferId, 1, 2, "30.00"));

        assertThat(second).isEqualTo(Result.DUPLICATE_IGNORED);
        assertThat(repo.balanceOf(1)).isEqualTo(Money.of("70.00", "USD")); // applied exactly once
    }

    @Test
    void incentive_is_credited_to_recipient_and_pool_funded_so_ledger_balances() {
        repo.seed(account(1, "100.00"));
        repo.seed(account(2, "50.00"));

        Result result = service(reward("1.00")).process(command(UUID.randomUUID(), 1, 2, "30.00"));

        assertThat(result).isEqualTo(Result.POSTED);
        assertThat(repo.balanceOf(1)).isEqualTo(Money.of("70.00", "USD"));
        assertThat(repo.balanceOf(2)).isEqualTo(Money.of("81.00", "USD")); // 50 + 30 + 1 reward
        assertThat(repo.ledgerSum().compareTo(BigDecimal.ZERO)).isZero();                              // pool debit balances it
    }

    @Test
    void incentive_failure_still_settles_the_transfer() {
        repo.seed(account(1, "100.00"));
        repo.seed(account(2, "50.00"));
        IncentivePort failing = transfer -> {
            throw new RuntimeException("incentive API down");
        };

        Result result = service(failing).process(command(UUID.randomUUID(), 1, 2, "30.00"));

        assertThat(result).isEqualTo(Result.POSTED);
        assertThat(repo.balanceOf(2)).isEqualTo(Money.of("80.00", "USD")); // settled, zero reward
    }

    private static IncentivePort noReward() {
        return transfer -> Money.zero(USD);
    }

    private static IncentivePort reward(String amount) {
        return transfer -> Money.of(amount, "USD");
    }

    // ---- in-memory port fakes ----

    private static final class FakeAccountRepository implements AccountRepositoryPort {
        private final Map<Long, Account> accounts = new HashMap<>();
        private final List<LedgerEntry> ledger = new ArrayList<>();
        private final Set<UUID> transfers = new HashSet<>();

        void seed(Account a) {
            accounts.put(a.id(), a);
        }

        Money balanceOf(long id) {
            return accounts.get(id).balance();
        }

        BigDecimal ledgerSum() {
            return ledger.stream()
                    .map(e -> e.amount().amount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        @Override
        public Optional<Account> findById(long id) {
            return Optional.ofNullable(accounts.get(id));
        }

        @Override
        public void saveAll(List<Account> toSave) {
            for (Account a : toSave) {
                accounts.put(a.id(), a);
            }
        }

        @Override
        public void appendLedger(List<LedgerEntry> entries) {
            ledger.addAll(entries);
            for (LedgerEntry e : entries) {
                transfers.add(e.transferId());
            }
        }

        @Override
        public boolean transferExists(UUID transferId) {
            return transfers.contains(transferId);
        }
    }

    private static final class FakeEvents implements EventPublisherPort {
        private final List<TransferRecorded> published = new ArrayList<>();

        @Override
        public void publish(TransferRecorded event) {
            published.add(event);
        }
    }
}
