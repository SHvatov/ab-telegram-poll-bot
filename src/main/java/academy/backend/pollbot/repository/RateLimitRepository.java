package academy.backend.pollbot.repository;

import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.SetParams;

public final class RateLimitRepository {

    private final UnifiedJedis redis;

    public RateLimitRepository(UnifiedJedis redis) {
        this.redis = redis;
    }

    /**
     * Tries to claim a request slot for {@code key} within a fixed window. Returns {@code 0} when the
     * slot was free (claimed now, via SET NX EX in one atomic call), otherwise the number of seconds
     * still left before the caller may retry.
     */
    public long acquire(String key, long windowSeconds) {
        String result = redis.set(key, "1", SetParams.setParams().nx().ex(windowSeconds));
        if ("OK".equals(result)) {
            return 0;
        }
        long ttl = redis.ttl(key);
        return ttl > 0 ? ttl : windowSeconds;
    }

    public void release(String key) {
        redis.del(key);
    }
}
