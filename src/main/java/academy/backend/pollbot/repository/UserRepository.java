package academy.backend.pollbot.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.SetParams;

import java.time.Instant;

public final class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    private final UnifiedJedis redis;
    private final long ttlSeconds;

    public UserRepository(UnifiedJedis redis, long ttlSeconds) {
        this.redis = redis;
        this.ttlSeconds = ttlSeconds;
    }

    public boolean registerIfAbsent(String username) {
        String result = redis.set(userKey(username), Instant.now().toString(),
                SetParams.setParams().nx().ex(ttlSeconds));
        boolean registered = "OK".equals(result);
        if (registered) {
            log.info("Registered new user '{}'", username);
        }
        return registered;
    }

    private static String userKey(String username) {
        return "user:" + username;
    }
}
