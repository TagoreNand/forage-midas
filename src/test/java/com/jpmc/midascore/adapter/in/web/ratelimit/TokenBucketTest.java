package com.jpmc.midascore.adapter.in.web.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketTest {

    @Test
    void allows_up_to_capacity_then_blocks() {
        // capacity 3, slow refill (3/min) so no whole token is added during the test window
        TokenBucket bucket = new TokenBucket(3, 3);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse(); // 4th request is rate-limited
    }
}
