package com.jpmc.midascore.adapter.out.incentive;

import com.jpmc.midascore.application.port.out.IncentivePort;
import com.jpmc.midascore.domain.model.Money;
import com.jpmc.midascore.domain.model.Transfer;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Driven (outbound) adapter for the external {@code transaction-incentive-api.jar}
 * (resolves Finding F10). Implements {@link IncentivePort} so the application never sees HTTP.
 *
 * <p>Pattern: Decorator (resilience). The Resilience4j stack — TimeLimiter, CircuitBreaker,
 * Retry, Bulkhead — wraps the call so the external service's latency/outage cannot break
 * core settlement. On failure the {@code fallback} returns a zero reward; the transfer still
 * completes and the reward can be settled later by a background job (ARCHITECTURE.md §10).
 */
@Component
public class IncentiveRestAdapter implements IncentivePort {

    private static final Logger log = LoggerFactory.getLogger(IncentiveRestAdapter.class);
    private static final Currency USD = Currency.getInstance("USD");

    private final RestClient client;

    public IncentiveRestAdapter(RestClient.Builder builder,
                                @Value("${general.incentive-api-url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    @TimeLimiter(name = "incentiveApi")
    @CircuitBreaker(name = "incentiveApi", fallbackMethod = "fallback")
    @Retry(name = "incentiveApi")
    @Bulkhead(name = "incentiveApi")
    public Money rewardFor(Transfer transfer) {
        IncentiveResponse response = client.post()
                .uri("/incentive")
                .body(new IncentiveRequest(
                        transfer.senderId(), transfer.recipientId(), transfer.amount().amount()))
                .retrieve()
                .body(IncentiveResponse.class);

        BigDecimal amount = response != null ? response.amount() : BigDecimal.ZERO;
        return Money.of(amount, USD);
    }

    /** Resilience fallback: never let a rewards outage block a payment. */
    @SuppressWarnings("unused")
    private Money fallback(Transfer transfer, Throwable t) {
        log.warn("Incentive API unavailable for transfer {} ({}); defaulting reward to zero",
                transfer.id(), t.toString());
        return Money.zero(USD);
    }

    record IncentiveRequest(long senderId, long recipientId, BigDecimal amount) {}
    record IncentiveResponse(BigDecimal amount) {}
}
