package academy.backend.pollbot.repository;

import academy.backend.pollbot.domain.ChatState;
import academy.backend.pollbot.redis.CurrentChatState;
import redis.clients.jedis.AbstractPipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class ChatViewRepository {

    private static final String REFRESHABLE_SET_KEY = "chatview:refreshable";

    // Sorted set of chatId -> last-activity epoch millis, but only for chats sitting in a non-MENU
    // state. The idle sweeper reads it to find sessions to bounce back to the menu; a chat that
    // reaches MENU (its resting state) is dropped, since there's nothing to time out from there.
    private static final String ACTIVITY_ZSET_KEY = "chatview:activity";

    private static final long VIEW_TTL_SECONDS = 60 * 60;

    private final UnifiedJedis redis;

    public ChatViewRepository(UnifiedJedis redis) {
        this.redis = redis;
    }

    public void setState(long chatId, ChatState state, int messageId, long userId) {
        String key = chatViewKey(chatId);
        redis.hset(key, Map.of(
                "state", state.name(),
                "messageId", String.valueOf(messageId),
                "userId", String.valueOf(userId)));
        redis.expire(key, VIEW_TTL_SECONDS);
        if (state == ChatState.MENU) {
            redis.zrem(ACTIVITY_ZSET_KEY, String.valueOf(chatId));
        } else {
            redis.zadd(ACTIVITY_ZSET_KEY, System.currentTimeMillis(), String.valueOf(chatId));
        }
    }

    public void markAsRefreshable(long chatId) {
        redis.sadd(REFRESHABLE_SET_KEY, String.valueOf(chatId));
    }

    public void unmarkAsRefreshable(long chatId) {
        redis.srem(REFRESHABLE_SET_KEY, String.valueOf(chatId));
    }

    public Optional<CurrentChatState> getState(long chatId) {
        Map<String, String> fields = redis.hgetAll(chatViewKey(chatId));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toState(chatId, fields));
    }

    /**
     * Reads {@code REFRESHABLE_SET_KEY} in bounded pages via SSCAN (never blocks Redis with a
     * single unbounded SMEMBERS), and fetches each page's chat-view hashes in one pipelined round
     * trip instead of one HGETALL per chat (which would be an N+1 network round trip per chat).
     */
    public List<CurrentChatState> listRefreshableStates() {
        List<CurrentChatState> result = new ArrayList<>();
        ScanParams params = new ScanParams().count(200);
        String cursor = ScanParams.SCAN_POINTER_START;
        do {
            ScanResult<String> scanResult = redis.sscan(REFRESHABLE_SET_KEY, cursor, params);
            cursor = scanResult.getCursor();
            result.addAll(fetchStates(scanResult.getResult(),
                    stale -> redis.srem(REFRESHABLE_SET_KEY, stale)));
        } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
        return result;
    }

    /**
     * Returns the chat states that have been sitting in a non-MENU state, untouched, for at least
     * {@code idleMillis} - the sessions the idle sweeper should bounce back to the menu.
     */
    public List<CurrentChatState> listIdleStates(long idleMillis) {
        double cutoff = System.currentTimeMillis() - idleMillis;
        List<String> chatIds = redis.zrangeByScore(ACTIVITY_ZSET_KEY, 0, cutoff);
        return fetchStates(chatIds, stale -> redis.zrem(ACTIVITY_ZSET_KEY, stale));
    }

    private List<CurrentChatState> fetchStates(List<String> chatIdStrs, Consumer<String[]> prune) {
        if (chatIdStrs.isEmpty()) {
            return List.of();
        }
        Map<String, Response<Map<String, String>>> responses = new LinkedHashMap<>();
        try (AbstractPipeline pipeline = redis.pipelined()) {
            for (String chatIdStr : chatIdStrs) {
                responses.put(chatIdStr, pipeline.hgetAll(chatViewKey(Long.parseLong(chatIdStr))));
            }
            pipeline.sync();
        }

        List<CurrentChatState> result = new ArrayList<>(chatIdStrs.size());
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, Response<Map<String, String>>> entry : responses.entrySet()) {
            Map<String, String> fields = entry.getValue().get();
            if (fields == null || fields.isEmpty()) {
                // The chat-view hash TTL'd out without a matching setState() call; drop the
                // now-stale membership instead of retrying it forever.
                stale.add(entry.getKey());
                continue;
            }
            result.add(toState(Long.parseLong(entry.getKey()), fields));
        }
        if (!stale.isEmpty()) {
            prune.accept(stale.toArray(new String[0]));
        }
        return result;
    }

    private static CurrentChatState toState(long chatId, Map<String, String> fields) {
        return new CurrentChatState(
                chatId,
                ChatState.valueOf(fields.get("state")),
                Integer.parseInt(fields.get("messageId")),
                Long.parseLong(fields.get("userId")));
    }

    private static String chatViewKey(long chatId) {
        return "chatview:" + chatId;
    }
}
