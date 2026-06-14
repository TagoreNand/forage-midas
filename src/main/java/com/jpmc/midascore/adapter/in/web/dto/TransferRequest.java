package com.jpmc.midascore.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/v1/transfers}. Bean Validation (Roadmap section 4.3) is
 * enforced by {@code @Valid} on the controller: ids are required and the amount must be a
 * positive money value, rejecting bad input before it reaches the domain.
 */
public record TransferRequest(

        @NotNull(message = "senderId is required")
        Long senderId,

        @NotNull(message = "recipientId is required")
        Long recipientId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        BigDecimal amount) {
}
