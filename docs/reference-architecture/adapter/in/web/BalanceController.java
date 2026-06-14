package com.jpmc.midascore.adapter.in.web;

import com.jpmc.midascore.application.port.out.AccountRepositoryPort;
import com.jpmc.midascore.domain.model.Account;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Driving (inbound) HTTP adapter — exposes {@code GET /balance?userId=}, the endpoint the
 * Forage {@code BalanceQuerier} calls on port 33400 (resolves the missing read API).
 *
 * <p>Reads come from the query side (CQRS, §2.5/§9): in production this hits the Redis hot
 * cache first and falls back to the projection. <strong>Security note (Roadmap §4.3):</strong>
 * once Spring Security lands in Phase 4 this becomes principal-scoped —
 * {@code @PreAuthorize("#userId == authentication.principal.accountId or hasRole('SUPPORT')")} —
 * so a user can only read their own balance instead of anyone's by id.
 */
@RestController
public class BalanceController {

    private final AccountRepositoryPort accounts;

    public BalanceController(AccountRepositoryPort accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> balance(@RequestParam long userId) {
        return accounts.findById(userId)
                .map(Account::balance)
                .map(money -> ResponseEntity.ok(new BalanceResponse(money.amount())))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Response shape kept compatible with the Forage {@code Balance} contract. */
    public record BalanceResponse(BigDecimal amount) {}
}
