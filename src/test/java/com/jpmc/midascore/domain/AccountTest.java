package com.jpmc.midascore.domain;

import com.jpmc.midascore.domain.model.Account;
import com.jpmc.midascore.domain.model.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private Account account(String balance) {
        return new Account(1L, "alice", Money.of(balance, "USD"), 0L);
    }

    @Test
    void canDebit_true_when_funds_sufficient() {
        assertThat(account("100.00").canDebit(Money.of("100.00", "USD"))).isTrue();
        assertThat(account("100.00").canDebit(Money.of("100.01", "USD"))).isFalse();
    }

    @Test
    void canDebit_false_for_non_positive_amount() {
        assertThat(account("100.00").canDebit(Money.of("0.00", "USD"))).isFalse();
        assertThat(account("100.00").canDebit(Money.of("-5.00", "USD"))).isFalse();
    }

    @Test
    void debit_and_credit_adjust_balance() {
        Account a = account("100.00");
        a.debit(Money.of("30.00", "USD"));
        assertThat(a.balance()).isEqualTo(Money.of("70.00", "USD"));
        a.credit(Money.of("5.00", "USD"));
        assertThat(a.balance()).isEqualTo(Money.of("75.00", "USD"));
    }

    @Test
    void debit_beyond_balance_throws() {
        assertThatThrownBy(() -> account("10.00").debit(Money.of("30.00", "USD")))
                .isInstanceOf(Account.InsufficientFundsException.class);
    }

    @Test
    void credit_must_be_positive() {
        assertThatThrownBy(() -> account("10.00").credit(Money.of("-1.00", "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
