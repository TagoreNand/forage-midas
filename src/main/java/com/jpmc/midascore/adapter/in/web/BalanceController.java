package com.jpmc.midascore.adapter.in.web;

import com.jpmc.midascore.application.port.in.GetBalanceUseCase;
import com.jpmc.midascore.domain.model.Money;
import com.jpmc.midascore.foundation.Balance;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound HTTP adapter - exposes {@code GET /balance?userId=}, the endpoint the Forage
 * {@code BalanceQuerier} calls on port 33400. Reads now go through the CQRS query side
 * ({@link GetBalanceUseCase}), which is cache-accelerated (Phase 3). Returns the existing
 * {@link Balance} contract ({@code {"amount": ...}}) for compatibility.
 *
 * <p>Security note (Phase 4): this becomes principal-scoped via {@code @PreAuthorize} once
 * Spring Security lands, so a user can only read their own balance.
 */
@RestController
public class BalanceController {

    private final GetBalanceUseCase balances;

    public BalanceController(GetBalanceUseCase balances) {
        this.balances = balances;
    }

    @GetMapping("/balance")
    public Balance balance(@RequestParam long userId) {
        Money money = balances.balanceOf(userId);
        return new Balance(money.amount().floatValue());
    }
}
