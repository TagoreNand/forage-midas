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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Orchestrates one transfer end-to-end. This is the only place the steps are sequenced;
 * the rules themselves live in the domain ({@link Account}, {@link Transfer}, {@link Money}).
 *
 * <p>Single Responsibility (SOLID's S): orchestration only — no transport, no SQL, no HTTP.
 * Depends exclusively on ports (D), so it is unit-testable with in-memory fakes in
 * milliseconds. The transactional boundary makes the ledger postings, balance update, and
 * outbox write atomic (ARCHITECTURE.md §7–§8).
 */
@Service
public class ProcessTransferService implements ProcessTransferUseCase {

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
        // 1) Idempotency: a redelivered Kafka message must not double-post (Finding F5).
        //    A Redis SETNX fast-path sits in front of this durable backstop (§8).
        if (accounts.transferExists(cmd.transferId())) {
            return Result.DUPLICATE_IGNORED;
        }

        // 2) Load both accounts (optimistic-locked). Unknown user => reject (Finding F8).
        Optional<Account> maybeSender = accounts.findById(cmd.senderId());
        Optional<Account> maybeRecipient = accounts.findById(cmd.recipientId());
        if (maybeSender.isEmpty() || maybeRecipient.isEmpty()) {
            return Result.REJECTED_INVALID;
        }
        Account sender = maybeSender.get();
        Account recipient = maybeRecipient.get();

        // 3) Build the validated domain intent (rejects self-transfer / non-positive amount).
        Transfer transfer = new Transfer(
                cmd.transferId(), cmd.senderId(), cmd.recipientId(), cmd.amount());

        // 4) Enforce the funds invariant before mutating anything.
        if (!sender.canDebit(transfer.amount())) {
            return Result.REJECTED_INSUFFICIENT_FUNDS;
        }

        // 5) Apply the balanced double-entry posting.
        sender.debit(transfer.amount());
        recipient.credit(transfer.amount());
        List<LedgerEntry> posting = LedgerEntry.postingFor(
                transfer.id(), transfer.senderId(), transfer.recipientId(), transfer.amount());

        accounts.appendLedger(posting);
        accounts.saveAll(List.of(sender, recipient));

        // 6) Emit the integration event via the outbox (atomic with the postings above).
        events.publish(TransferRecorded.now(
                transfer.id(), transfer.senderId(), transfer.recipientId(), transfer.amount()));

        // 7) Reward settlement. The port is circuit-breaker-wrapped, so an incentive-API
        //    outage degrades to a zero reward and the transfer still settles (§10).
        Money reward = incentives.rewardFor(transfer);
        if (reward.isPositive()) {
            recipient.credit(reward);
            accounts.appendLedger(LedgerEntry.postingFor(
                    transfer.id(), transfer.senderId(), transfer.recipientId(), reward));
            accounts.saveAll(List.of(recipient));
        }

        return Result.POSTED;
    }
}
