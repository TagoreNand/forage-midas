package com.jpmc.midascore.adapter.in.web;

import com.jpmc.midascore.adapter.in.web.dto.TransferRequest;
import com.jpmc.midascore.application.port.in.ProcessTransferUseCase;
import com.jpmc.midascore.application.port.in.ProcessTransferUseCase.Result;
import com.jpmc.midascore.application.port.in.ProcessTransferUseCase.TransferCommand;
import com.jpmc.midascore.domain.model.Money;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Currency;
import java.util.UUID;

/**
 * Synchronous HTTP write ingress for transfers (Phase 4) - a secured complement to the Kafka
 * consumer. Demonstrates the full enterprise request path:
 *
 * <ul>
 *   <li><b>AuthZ (RBAC):</b> {@code @PreAuthorize("hasRole('TELLER')")} - enforced only when
 *       security is enabled (method security is off in the permit-all profile).</li>
 *   <li><b>Validation:</b> {@code @Valid} on the request body (Bean Validation).</li>
 *   <li><b>Idempotency:</b> an optional {@code Idempotency-Key} header maps to a deterministic
 *       transfer id, so a retried POST is deduped by the ledger (reuses the Phase 2 machinery).</li>
 *   <li><b>Rate limiting:</b> applied by {@code RateLimitingFilter} on the {@code /api/**} path.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);
    private static final Currency USD = Currency.getInstance("USD");

    private final ProcessTransferUseCase processTransfer;

    public TransferController(ProcessTransferUseCase processTransfer) {
        this.processTransfer = processTransfer;
    }

    @PostMapping
    @PreAuthorize("hasRole('TELLER')")
    public ResponseEntity<TransferResponse> initiate(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        UUID transferId = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8))
                : UUID.randomUUID();

        TransferCommand command = new TransferCommand(
                transferId, request.senderId(), request.recipientId(),
                Money.of(request.amount(), USD));

        Result result = processTransfer.process(command);
        log.info("REST transfer {} {} -> {} amount {} : {}",
                transferId, request.senderId(), request.recipientId(), request.amount(), result);

        HttpStatus status = switch (result) {
            case POSTED -> HttpStatus.CREATED;
            case DUPLICATE_IGNORED -> HttpStatus.OK;
            case REJECTED_INSUFFICIENT_FUNDS -> HttpStatus.CONFLICT;
            case REJECTED_INVALID -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(new TransferResponse(transferId.toString(), result.name()));
    }

    /** Response body echoing the assigned transfer id and the settlement outcome. */
    public record TransferResponse(String transferId, String status) {
    }
}
