package com.jpmc.midascore.domain;

import com.jpmc.midascore.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void addition_is_exact_unlike_float() {
        // 0.1 + 0.2 == 0.3 exactly (the canonical float bug this VO eliminates, Finding F1).
        Money sum = Money.of("0.10", "USD").plus(Money.of("0.20", "USD"));
        assertThat(sum).isEqualTo(Money.of("0.30", "USD"));
    }

    @Test
    void subtraction_and_negation() {
        Money result = Money.of("5.00", "USD").minus(Money.of("2.50", "USD"));
        assertThat(result).isEqualTo(Money.of("2.50", "USD"));
        assertThat(result.negated()).isEqualTo(Money.of("-2.50", "USD"));
    }

    @Test
    void sign_predicates() {
        assertThat(Money.of("1.00", "USD").isPositive()).isTrue();
        assertThat(Money.of("-1.00", "USD").isNegative()).isTrue();
        assertThat(Money.zero(java.util.Currency.getInstance("USD")).isPositive()).isFalse();
    }

    @Test
    void comparison_requires_same_currency() {
        assertThat(Money.of("10.00", "USD").isGreaterThanOrEqual(Money.of("10.00", "USD"))).isTrue();
        assertThat(Money.of("9.99", "USD").isGreaterThanOrEqual(Money.of("10.00", "USD"))).isFalse();
    }

    @Test
    void mixing_currencies_is_rejected() {
        assertThatThrownBy(() -> Money.of("1.00", "USD").plus(Money.of("1.00", "EUR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scale_is_normalised_to_two_dp() {
        assertThat(Money.of(new BigDecimal("2"), java.util.Currency.getInstance("USD")))
                .isEqualTo(Money.of("2.00", "USD"));
    }
}
