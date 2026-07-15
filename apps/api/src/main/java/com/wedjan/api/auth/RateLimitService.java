package com.wedjan.api.auth;

import com.wedjan.api.common.ApiException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Fixed-window rate limiting on Redis (INCR + EXPIRE). Fails open if Redis
 * is unreachable — availability of auth beats strictness of the limit.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Throws 429 when more than {@code limit} hits occur within {@code window}. */
    public void check(String bucket, String key, int limit, Duration window) {
        String redisKey = "rl:" + bucket + ":" + key;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redis.expire(redisKey, window);
            }
            if (count != null && count > limit) {
                throw ApiException.rateLimited("Too many attempts — try again shortly");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Rate limit check unavailable for {} — failing open", redisKey, e);
        }
    }
}
