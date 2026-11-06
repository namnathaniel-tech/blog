package dev.blog.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * In-memory token buckets keyed by (limit name, subject). Single-instance only (D-013).
 * Usage: {@code rateLimiter.consumeOrThrow(Limits.OTP_REQUEST_PER_IP, ipHash);}
 */
@Component
public class RateLimiter {

    /** A named limit: at most {@code capacity} events per {@code period}, refilled greedily. */
    public record Limit(String name, long capacity, Duration period) {
    }

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.DAYS)
            .maximumSize(100_000)
            .build();

    /** @return true if allowed (and consumed), false if the limit is exhausted. */
    public boolean tryConsume(Limit limit, String subject) {
        return probe(limit, subject).isConsumed();
    }

    /** @throws RateLimitExceededException if the limit is exhausted. */
    public void consumeOrThrow(Limit limit, String subject) {
        ConsumptionProbe probe = probe(limit, subject);
        if (!probe.isConsumed()) {
            throw new RateLimitExceededException(
                    TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        }
    }

    /** Clears all buckets. Intended for tests. */
    public void reset() {
        buckets.invalidateAll();
    }

    private ConsumptionProbe probe(Limit limit, String subject) {
        Bucket bucket = buckets.get(limit.name() + ':' + subject, k -> Bucket.builder()
                .addLimit(l -> l.capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.period()))
                .build());
        return bucket.tryConsumeAndReturnRemaining(1);
    }
}
