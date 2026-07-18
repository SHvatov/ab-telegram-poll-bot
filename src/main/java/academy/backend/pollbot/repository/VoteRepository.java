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

    public boolean saveVoteIfAbsent(String username, String memeCode, Rating rating) {
        Object result = redis.eval(SAVE_VOTE_SCRIPT,
                List.of(userVotesKey(username), memeRatingKey(memeCode)),
                List.of(memeCode, rating.name(), String.valueOf(ttlSeconds)));
        return Objects.equals(result, 1L);
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

    private static String userVotesKey(String username) {
        return "user:" + username + ":votes";
    }

    private static String memeRatingKey(String memeCode) {
        return "meme:" + memeCode + ":rating";
    }
}
