package com.jpmc.midascore.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for an immutable, append-only ledger posting (Findings F2/F3).
 *
 * <p>The amount is stored as <strong>signed minor units</strong> (a {@code long}: negative =
 * debit, positive = credit) — precise, unlike the legacy {@code float} balance column. The
 * index on {@code transfer_id} backs the idempotency check and groups each transfer's entries.
 */
@Entity
@Table(name = "ledger_entry", indexes = {
        @Index(name = "idx_ledger_transfer", columnList = "transfer_id"),
        @Index(name = "idx_ledger_account", columnList = "account_id")
})
public class LedgerEntryEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private long accountId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntryEntity() {
    }

    public LedgerEntryEntity(UUID id, long accountId, long amountMinor,
                             UUID transferId, String currency, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.amountMinor = amountMinor;
        this.transferId = transferId;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public long getAccountId() {
        return accountId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
