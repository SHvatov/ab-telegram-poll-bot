package academy.backend.pollbot.repository;

import academy.backend.pollbot.domain.Rating;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.AbstractPipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.UnifiedJedis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class VoteRepository {

    private static final Logger log = LoggerFactory.getLogger(VoteRepository.class);

    // Atomically records the user's vote (KEYS[1], only if absent) and bumps the meme's rating
    // counter (KEYS[2]) in a single round trip: HSETNX + HINCRBY as one uninterruptible Redis
    // Lua script, so a crash or dropped connection between the two writes can't happen.
    // ARGV[1] = meme code, ARGV[2] = rating name, ARGV[3] = TTL (seconds) for the user's vote hash.
    private static final String SAVE_VOTE_SCRIPT = """
            if redis.call('HSETNX', KEYS[1], ARGV[1], ARGV[2]) == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[3])
                redis.call('HINCRBY', KEYS[2], ARGV[2], 1)
                return 1
            else
                return 0
            end
            """;

    private final UnifiedJedis redis;
    private final long ttlSeconds;

    public VoteRepository(UnifiedJedis redis, long ttlSeconds) {
        this.redis = redis;
        this.ttlSeconds = ttlSeconds;
    }

    public Optional<Rating> getUserVote(long userId, String memeCode) {
        String value = redis.hget(userVotesKey(userId), memeCode);
        return value == null ? Optional.empty() : Optional.of(Rating.valueOf(value));
    }

    public Map<String, Rating> getUserVotes(long userId) {
        return redis.hgetAll(userVotesKey(userId)).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Rating.valueOf(e.getValue())));
    }

    public boolean saveVoteIfAbsent(long userId, String memeCode, Rating rating) {
        Object result = redis.eval(SAVE_VOTE_SCRIPT,
                List.of(userVotesKey(userId), memeRatingKey(memeCode)),
                List.of(memeCode, rating.name(), String.valueOf(ttlSeconds)));
        boolean saved = Objects.equals(result, 1L);
        if (saved) {
            log.info("User {} voted '{}' for meme '{}'", userId, rating, memeCode);
        } else {
            log.debug("User {} already voted for meme '{}', ignoring", userId, memeCode);
        }
        return saved;
    }

    public Optional<Rating> getGlobalRating(String memeCode) {
        Map<String, String> counts = redis.hgetAll(memeRatingKey(memeCode));
        if (counts.isEmpty()) {
            return Optional.empty();
        }
        return counts.entrySet().stream()
                .map(e -> Map.entry(Rating.valueOf(e.getKey()), Long.parseLong(e.getValue())))
                .max(Comparator.<Map.Entry<Rating, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(e -> -e.getKey().rank()))
                .map(Map.Entry::getKey);
    }

    /**
     * How many memes each of the given users has voted for (HLEN of their votes hash), in one
     * pipelined round trip. The returned list lines up positionally with {@code userIds}.
     */
    public List<Long> votedMemeCounts(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        List<Response<Long>> responses = new ArrayList<>(userIds.size());
        try (AbstractPipeline pipeline = redis.pipelined()) {
            for (long userId : userIds) {
                responses.add(pipeline.hlen(userVotesKey(userId)));
            }
            pipeline.sync();
        }
        List<Long> counts = new ArrayList<>(responses.size());
        for (Response<Long> response : responses) {
            counts.add(response.get());
        }
        return counts;
    }

    /** Total number of votes (== distinct voters) a meme has received, summed across all ratings. */
    public long voterCount(String memeCode) {
        return redis.hgetAll(memeRatingKey(memeCode)).values().stream()
                .mapToLong(Long::parseLong)
                .sum();
    }

    private static String userVotesKey(long userId) {
        return "user:" + userId + ":votes";
    }

    private static String memeRatingKey(String memeCode) {
        return "meme:" + memeCode + ":rating";
    }
}
