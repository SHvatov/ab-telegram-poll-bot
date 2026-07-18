package academy.backend.pollbot.repository;

import academy.backend.pollbot.domain.Rating;
import redis.clients.jedis.UnifiedJedis;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class VoteRepository {

    /**
     * Atomically records the user's vote and bumps the meme's rating counter, or does neither if
     * the user already voted. A Lua script is the only way to make "check, then act on two keys"
     * atomic in Redis short of MULTI/EXEC with WATCH; this is simpler and just as safe, since the
     * whole script runs as a single, uninterruptible server-side step.
     * <p>
     * KEYS[1] = the user's vote hash, KEYS[2] = the meme's rating-counter hash.
     * ARGV[1] = meme code, ARGV[2] = rating name, ARGV[3] = TTL (seconds) for the user's vote hash.
     */
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

    public Optional<Rating> getUserVote(String username, String memeCode) {
        String value = redis.hget(userVotesKey(username), memeCode);
        return value == null ? Optional.empty() : Optional.of(Rating.valueOf(value));
    }

    public Map<String, Rating> getUserVotes(String username) {
        return redis.hgetAll(userVotesKey(username)).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Rating.valueOf(e.getValue())));
    }

    /**
     * Records the user's vote, unless they have already voted for this meme (votes cannot be changed).
     *
     * @return true if the vote was recorded, false if the user had already voted
     */
    public boolean saveVoteIfAbsent(String username, String memeCode, Rating rating) {
        Object result = redis.eval(SAVE_VOTE_SCRIPT,
                List.of(userVotesKey(username), memeRatingKey(memeCode)),
                List.of(memeCode, rating.name(), String.valueOf(ttlSeconds)));
        return Objects.equals(result, 1L);
    }

    /**
     * The meme's global rating is the most frequently cast vote, read from a small per-meme
     * counter hash (at most one entry per {@link Rating} value) rather than the full history of
     * votes, so this stays cheap no matter how many votes a meme has collected. Ties are broken in
     * favor of the better rating (lower {@link Rating#rank()}).
     */
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

    private static String userVotesKey(String username) {
        return "user:" + username + ":votes";
    }

    /**
     * Rating -> vote count for one meme. No TTL: unlike per-user data, the aggregate rating is
     * meant to persist for as long as the meme itself is configured, independent of any single
     * user's data lifecycle.
     */
    private static String memeRatingKey(String memeCode) {
        return "meme:" + memeCode + ":rating";
    }
}
