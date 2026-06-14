package com.jpmc.midascore.adapter.out.cache;

import com.jpmc.midascore.application.port.out.BalanceCachePort;
import com.jpmc.midascore.domain.model.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed {@link BalanceCachePort} (Phase 3), active only when {@code midas.cache.enabled=true}.
 *
 * <p>Implements cache-aside storage with a TTL bound on staleness and <strong>graceful
 * degradation</strong>: every Redis call is wrapped so a connection failure becomes a miss or a
 * no-op rather than an error. A cold or unavailable cache therefore degrades latency, never
 * correctness (reads fall back to the database). Values are stored as {@code "<amount> <CCY>"}.
 */
@Component
@ConditionalOnProperty(prefix = "midas.cache", name = "enabled", havingValue = "true")
public class RedisBalanceCacheAdapter implements BalanceCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisBalanceCacheAdapter.class);
    private static final String KEY_PREFIX = "midas:balance:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisBalanceCacheAdapter(StringRedisTemplate redis,
                                    @Value("${midas.cache.ttl-seconds:60}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public Optional<Money> get(long accountId) {
        try {
            String value = redis.opsForValue().get(key(accountId));
            return value == null ? Optional.empty() : Optional.of(parse(value));
        } catch (Exception e) {
            log.debug("Redis get failed for {} ({}); treating as miss", accountId, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public void put(long accountId, Money balance) {
        try {
            redis.opsForValue().set(key(accountId), balance.toString(), ttl);
        } catch (Exception e) {
            log.debug("Redis put failed for {} ({}); ignoring", accountId, e.toString());
        }
    }

    @Override
    public void evict(long accountId) {
        try {
            redis.delete(key(accountId));
        } catch (Exception e) {
            log.debug("Redis evict failed for {} ({}); ignoring", accountId, e.toString());
        }
    }

    private static String key(long accountId) {
        return KEY_PREFIX + accountId;
    }

    /** Parse a stored {@code "<amount> <CCY>"} value (the format of {@link Money#toString()}). */
    private static Money parse(String value) {
        String[] parts = value.split(" ");
        return Money.of(parts[0], parts[1]);
    }
}
