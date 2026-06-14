package com.jpmc.midascore.application.port.in;

import com.jpmc.midascore.domain.model.Money;

import java.util.UUID;

/**
 * Driving port (inbound) — the application's API to the outside world.
 *
 * <p>Hexagonal architecture: inbound adapters (the Kafka listener, the REST controller)
 * depend on this interface, never on the concrete service. That inversion (SOLID's D) is
 * what lets us test the use case with fakes and swap transports without touching logic.
 */
public interface ProcessTransferUseCase {

    Result process(TransferCommand command);

    /** Input DTO — carries the idempotency key so redeliveries are recognised. */
    record TransferCommand(UUID transferId, long senderId, long recipientId, Money amount) {}

    enum Result { POSTED, DUPLICATE_IGNORED, REJECTED_INSUFFICIENT_FUNDS, REJECTED_INVALID }
}
