package com.jpmc.midascore.application.port.in;

import com.jpmc.midascore.domain.model.Money;

/**
 * Driving port for the CQRS query side (Phase 3): read an account's current balance.
 *
 * <p>Separating reads behind their own use case lets the query path be served from a cache /
 * read model independently of the write path, which keeps settlement lean and lets reads scale.
 */
public interface GetBalanceUseCase {

    /** Current balance for the account, or zero if the account is unknown. */
    Money balanceOf(long accountId);
}
