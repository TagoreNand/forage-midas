package com.jpmc.midascore.domain.model;

/**
 * Account aggregate root — the consistency boundary for money movement.
 *
 * <p>Resolves Findings F6 (no concurrency control) and F12 (anemic model). Unlike the
 * baseline {@code UserRecord} — a data bag with a public {@code setBalance(float)} — this
 * aggregate <em>owns its invariants</em>: it decides whether it can be debited and never
 * allows itself to go negative through normal operations. The {@code version} field backs
 * JPA optimistic locking so two concurrent transfers cannot silently overwrite each other
 * (lost-update race); a conflicting write fails and is retried.
 */
public class Account {

    private final long id;
    private final String name;
    private Money balance;
    private long version; // optimistic-lock token (maps to @Version on the JPA entity)

    public Account(long id, String name, Money balance, long version) {
        this.id = id;
        this.name = name;
        this.balance = balance;
        this.version = version;
    }

    /** Specification-style guard used by the application service before posting. */
    public boolean canDebit(Money amount) {
        if (!amount.isPositive()) {
            return false; // reject zero/negative transfers (Finding F8)
        }
        return balance.isGreaterThanOrEqual(amount);
    }

    public void debit(Money amount) {
        if (!canDebit(amount)) {
            throw new InsufficientFundsException(id, balance, amount);
        }
        this.balance = balance.minus(amount);
    }

    public void credit(Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.balance = balance.plus(amount);
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Money balance() {
        return balance;
    }

    public long version() {
        return version;
    }

    /** Domain exception — distinct, catchable, and meaningful (not a generic failure). */
    public static final class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(long accountId, Money balance, Money requested) {
            super("Account %d has %s, cannot debit %s".formatted(accountId, balance, requested));
        }
    }
}
