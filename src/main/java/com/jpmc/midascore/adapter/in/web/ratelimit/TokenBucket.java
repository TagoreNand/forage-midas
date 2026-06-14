package com.jpmc.midascore.adapter.in.web.ratelimit;

/**
 * A thread-safe token bucket: up to {@code capacity} requests are allowed, refilling
 * continuously at {@code refillPerMinute} tokens/minute. Used per client by
 * {@link RateLimitingFilter}.
 *
 * <p>This in-memory implementation limits per node; a distributed deployment would back it
 * with Redis (e.g. Bucket4j) so the limit is global across replicas (a Phase 4.x follow-up).
 */
public final class TokenBucket {

    private final double capacity;
    private final double refillPerMilli;
    private double tokens;
    private long lastRefillMillis;

    public TokenBucket(long capacity, long refillPerMinute) {
        this.capacity = capacity;
        this.refillPerMilli = refillPerMinute / 60_000.0;
        this.tokens = capacity;
        this.lastRefillMillis = System.currentTimeMillis();
    }

    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        double add = (now - lastRefillMillis) * refillPerMilli;
        if (add > 0) {
            tokens = Math.min(capacity, tokens + add);
            lastRefillMillis = now;
        }
    }
}
