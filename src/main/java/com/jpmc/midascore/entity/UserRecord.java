package com.jpmc.midascore.entity;

import jakarta.persistence.*;

@Entity
public class UserRecord {

    @Id
    @GeneratedValue()
    private long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private float balance;

    // Optimistic-lock token (Finding F6). Hibernate increments it on every update and
    // raises ObjectOptimisticLockingFailureException if two transactions race the same row,
    // so concurrent transfers can no longer silently overwrite each other (lost-update).
    @Version
    @Column(nullable = false)
    private long version;

    protected UserRecord() {
    }

    public UserRecord(String name, float balance) {
        this.name = name;
        this.balance = balance;
    }

    @Override
    public String toString() {
        return String.format("User[id=%d, name='%s', balance='%f'", id, name, balance);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public float getBalance() {
        return balance;
    }

    public void setBalance(float balance) {
        this.balance = balance;
    }

    public long getVersion() {
        return version;
    }
}
