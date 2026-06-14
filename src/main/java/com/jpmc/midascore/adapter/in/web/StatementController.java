package com.jpmc.midascore.adapter.in.web;

import com.jpmc.midascore.application.port.in.GetStatementUseCase;
import com.jpmc.midascore.application.port.in.GetStatementUseCase.Statement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Secured statement endpoint (Phase 5): {@code GET /api/v1/accounts/{id}/statement}. Restricted to
 * support/teller roles when security is enabled, and rate-limited as part of {@code /api/**}.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class StatementController {

    private final GetStatementUseCase statements;

    public StatementController(GetStatementUseCase statements) {
        this.statements = statements;
    }

    @GetMapping("/{accountId}/statement")
    @PreAuthorize("hasAnyRole('TELLER','SUPPORT')")
    public Statement statement(@PathVariable long accountId,
                               @RequestParam(defaultValue = "50") int limit) {
        return statements.statementFor(accountId, limit);
    }
}
