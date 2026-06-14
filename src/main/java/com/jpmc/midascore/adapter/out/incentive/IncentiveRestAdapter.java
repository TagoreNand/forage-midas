package com.jpmc.midascore.adapter.out.incentive;

import com.jpmc.midascore.application.port.out.IncentivePort;
import com.jpmc.midascore.domain.model.Money;
import com.jpmc.midascore.domain.model.Transfer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Outbound adapter for the external {@code transaction-incentive-api.jar} (Finding F10),
 * implementing {@link IncentivePort} so the application never sees HTTP.
 *
 * <p>The Resilience4j circuit breaker + retry wrap the call; on failure the {@code fallback}
 * returns a zero reward, so a rewards-service outage never blocks settlement. A 2s connect/read
 * timeout bounds latency. This lets Midas run with or without the jar present.
 *
 * <p>NOTE: confirm the request path/JSON against the bundled jar — adjust {@code /incentive}
 * and the {@link IncentiveResponse} field name if the service's contract differs.
 */
@Component
public class IncentiveRestAdapter implements IncentivePort {

    private static final Logger log = LoggerFactory.getLogger(IncentiveRestAdapter.class);
    private static final Currency USD = Currency.getInstance("USD");

    private final RestClient client;

    public IncentiveRestAdapter(RestClient.Builder builder,
                                @Value("${general.incentive-api-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    @CircuitBreaker(name = "incentiveApi", fallbackMethod = "fallback")
    @Retry(name = "incentiveApi")
    public Money rewardFor(Transfer transfer) {
        IncentiveResponse response = client.post()
                .uri("/incentive")
                .body(new IncentiveRequest(
                        transfer.senderId(), transfer.recipientId(), transfer.amount().amount()))
                .retrieve()
                .body(IncentiveResponse.class);

        BigDecimal amount = (response != null && response.amount() != null)
                ? response.amount() : BigDecimal.ZERO;
        return Money.of(amount, USD);
    }

    @SuppressWarnings("unused")
    private Money fallback(Transfer transfer, Throwable t) {
        log.warn("Incentive API unavailable for transfer {} ({}); reward defaults to zero",
                transfer.id(), t.toString());
        return Money.zero(USD);
    }

    record IncentiveRequest(long senderId, long recipientId, BigDecimal amount) {
    }

    record IncentiveResponse(BigDecimal amount) {
    }
}
