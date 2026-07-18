package academy.backend.pollbot.repository;

import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.SetParams;

import java.time.Instant;

public final class UserRepository {

    private final UnifiedJedis redis;
    private final long ttlSeconds;

    public UserRepository(UnifiedJedis redis, long ttlSeconds) {
        this.redis = redis;
        this.ttlSeconds = ttlSeconds;
    }

    public boolean registerIfAbsent(String username) {
        String result = redis.set(userKey(username), Instant.now().toString(),
                SetParams.setParams().nx().ex(ttlSeconds));
        return "OK".equals(result);
    }

    private static String userKey(String username) {
        return "user:" + username;
    }
}
