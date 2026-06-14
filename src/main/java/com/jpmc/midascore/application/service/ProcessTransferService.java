package com.jpmc.midascore.application.service;

import com.jpmc.midascore.application.port.in.ProcessTransferUseCase;
import com.jpmc.midascore.application.port.out.AccountRepositoryPort;
import com.jpmc.midascore.application.port.out.EventPublisherPort;
import com.jpmc.midascore.application.port.out.IncentivePort;
import com.jpmc.midascore.domain.event.TransferRecorded;
import com.jpmc.midascore.domain.model.Account;
import com.jpmc.midascore.domain.model.Money;
import com.jpmc.midascore.domain.model.Transfer;
import com.jpmc.midascore.domain.model.ledger.LedgerEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates one transfer end-to-end (Phase 1). The rules live in the domain
 * ({@link Account}, {@link Transfer}, {@link Money}); this service only sequences them.
 *
 * <p>Single Responsibility: orchestration only. Depends exclusively on ports, so it unit-tests
 * with in-memory fakes in milliseconds (see {@code ProcessTransferServiceTest}).
 */
@Service
public class ProcessTransferService implements ProcessTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessTransferService.class);

    /**
     * Well-known funding account for rewards. Crediting a recipient with an incentive is
     * balanced by an equal debit here, so the double-entry ledger still nets to zero
     * (conservation of value). The pool is ledger-only; it has no balance projection row.
     */
    static final long INCENTIVE_POOL_ID = 0L;

    private final AccountRepositoryPort accounts;
    private final IncentivePort incentives;
    private final EventPublisherPort events;

    public ProcessTransferService(AccountRepositoryPort accounts,
                                  IncentivePort incentives,
                                  EventPublisherPort events) {
        this.accounts = accounts;
        this.incentives = incentives;
        this.events = events;
    }

    @Override
    @Transactional
    public Result process(TransferCommand cmd) {
        // 1) Idempotency: a redelivered message must not double-post (Finding F5).
        if (accounts.transferExists(cmd.transferId())) {
            log.debug("Transfer {} already processed; ignoring duplicate", cmd.transferId());
            return Result.DUPLICATE_IGNORED;
        }

        // 2) Both parties must exist (Finding F8).
        Optional<Account> maybeSender = accounts.findById(cmd.senderId());
        Optional<Account> maybeRecipient = accounts.findById(cmd.recipientId());
        if (maybeSender.isEmpty() || maybeRecipient.isEmpty()) {
            log.info("Rejecting transfer {}: unknown sender or recipient", cmd.transferId());
            return Result.REJECTED_INVALID;
        }
        Account sender = maybeSender.get();
        Account recipient = maybeRecipient.get();

        // 3) Build the validated intent (rejects self-transfer / non-positive amount).
        Transfer transfer;
        try {
            transfer = new Transfer(cmd.transferId(), cmd.senderId(), cmd.recipientId(), cmd.amount());
        } catch (IllegalArgumentException e) {
            log.info("Rejecting transfer {}: {}", cmd.transferId(), e.getMessage());
            return Result.REJECTED_INVALID;
        }

        // 4) Enforce the funds invariant before mutating anything (Finding F8).
        if (!sender.canDebit(transfer.amount())) {
            log.info("Rejecting transfer {}: insufficient funds", cmd.transferId());
            return Result.REJECTED_INSUFFICIENT_FUNDS;
        }

        // 5) Apply the balanced double-entry posting for the transfer.
        sender.debit(transfer.amount());
        recipient.credit(transfer.amount());
        List<LedgerEntry> entries = new ArrayList<>(LedgerEntry.postingFor(
                transfer.id(), transfer.senderId(), transfer.recipientId(), transfer.amount()));

        // 6) Reward settlement (Finding F10). The port is resilience-wrapped and degrades to
        //    a zero reward on failure, so a rewards outage never blocks a payment. Funded by
        //    the incentive pool so the ledger remains balanced.
        Money reward = safeReward(transfer);
        if (reward.isPositive()) {
            recipient.credit(reward);
            entries.addAll(LedgerEntry.postingFor(
                    transfer.id(), INCENTIVE_POOL_ID, transfer.recipientId(), reward));
        }

        // 7) Persist: append immutable ledger entries, then the updated balance projections.
        accounts.appendLedger(entries);
        accounts.saveAll(List.of(sender, recipient));

        // 8) Emit the integration event (Phase 2 routes this through a Kafka outbox).
        events.publish(TransferRecorded.now(
                transfer.id(), transfer.senderId(), transfer.recipientId(), transfer.amount()));

        log.info("Posted transfer {}: {} -> {} amount {}{}",
                transfer.id(), transfer.senderId(), transfer.recipientId(), transfer.amount(),
                reward.isPositive() ? " (+reward " + reward + ")" : "");
        return Result.POSTED;
    }

    private Money safeReward(Transfer transfer) {
        try {
            Money reward = incentives.rewardFor(transfer);
            if (reward == null || reward.isNegative()) {
                return Money.zero(transfer.amount().currency());
            }
            return reward;
        } catch (Exception e) {
            log.warn("Incentive lookup failed for transfer {}; defaulting reward to zero ({})",
                    transfer.id(), e.toString());
            return Money.zero(transfer.amount().currency());
        }
    }
}
