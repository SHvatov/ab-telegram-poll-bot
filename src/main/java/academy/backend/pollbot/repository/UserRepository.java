package academy.backend.pollbot.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    // Membership set of every user id we've seen, so admin tooling can enumerate/count users
    // without SCANning the whole keyspace for "user:*".
    private static final String USERS_SET_KEY = "users";

    private final UnifiedJedis redis;
    private final long ttlSeconds;

    public UserRepository(UnifiedJedis redis, long ttlSeconds) {
        this.redis = redis;
        this.ttlSeconds = ttlSeconds;
    }

    public boolean registerIfAbsent(long userId) {
        String result = redis.set(userKey(userId), Instant.now().toString(),
                SetParams.setParams().nx().ex(ttlSeconds));
        boolean registered = "OK".equals(result);
        redis.sadd(USERS_SET_KEY, String.valueOf(userId));
        if (registered) {
            log.info("Registered new user {}", userId);
        }
        return registered;
    }

    public long count() {
        return redis.scard(USERS_SET_KEY);
    }

    public List<Long> allUserIds() {
        List<Long> ids = new ArrayList<>();
        ScanParams params = new ScanParams().count(500);
        String cursor = ScanParams.SCAN_POINTER_START;
        do {
            ScanResult<String> result = redis.sscan(USERS_SET_KEY, cursor, params);
            cursor = result.getCursor();
            result.getResult().forEach(id -> ids.add(Long.parseLong(id)));
        } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
        return ids;
    }

    private static String userKey(long userId) {
        return "user:" + userId;
    }
}
