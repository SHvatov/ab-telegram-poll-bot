package academy.backend.pollbot.repository;

import academy.backend.pollbot.domain.Rating;
import redis.clients.jedis.UnifiedJedis;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class VoteRepository {

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
        String userVotesKey = userVotesKey(username);
        long added = redis.hsetnx(userVotesKey, memeCode, rating.name());
        if (added == 0) {
            return false;
        }
        redis.expire(userVotesKey, ttlSeconds);

        String globalVotesKey = memeVotesKey(memeCode);
        redis.rpush(globalVotesKey, rating.name());
        redis.expire(globalVotesKey, ttlSeconds);
        return true;
    }

    /**
     * The meme's global rating is the most frequently cast vote. Ties are broken in favor of
     * the better rating (lower {@link Rating#rank()}).
     */
    public Optional<Rating> getGlobalRating(String memeCode) {
        List<String> votes = redis.lrange(memeVotesKey(memeCode), 0, -1);
        if (votes.isEmpty()) {
            return Optional.empty();
        }
        Map<Rating, Long> counts = votes.stream()
                .map(Rating::valueOf)
                .collect(Collectors.groupingBy(r -> r, Collectors.counting()));
        return counts.entrySet().stream()
                .max(Comparator.<Map.Entry<Rating, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(e -> -e.getKey().rank()))
                .map(Map.Entry::getKey);
    }

    private static String userVotesKey(String username) {
        return "user:" + username + ":votes";
    }

    private static String memeVotesKey(String memeCode) {
        return "meme:" + memeCode + ":votes";
    }
}
