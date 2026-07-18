package academy.backend.pollbot.repository;

import academy.backend.pollbot.domain.ChatState;
import academy.backend.pollbot.redis.CurrentChatState;
import redis.clients.jedis.UnifiedJedis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ChatViewRepository {

    private static final String REFRESHABLE_SET_KEY = "chatview:refreshable";

    private static final long VIEW_TTL_SECONDS = 60 * 60;

    private final UnifiedJedis redis;

    public ChatViewRepository(UnifiedJedis redis) {
        this.redis = redis;
    }

    public void setState(long chatId, ChatState state, int messageId, String username) {
        String key = chatViewKey(chatId);
        redis.hset(key, Map.of(
                "state", state.name(),
                "messageId", String.valueOf(messageId),
                "username", username));
        redis.expire(key, VIEW_TTL_SECONDS);

        String chatIdStr = String.valueOf(chatId);
        if (state.isRefreshable()) {
            redis.sadd(REFRESHABLE_SET_KEY, chatIdStr);
        } else {
            redis.srem(REFRESHABLE_SET_KEY, chatIdStr);
        }
    }

    public Optional<CurrentChatState> getState(long chatId) {
        Map<String, String> fields = redis.hgetAll(chatViewKey(chatId));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toState(chatId, fields));
    }

    public List<CurrentChatState> listRefreshableStates() {
        Set<String> chatIds = redis.smembers(REFRESHABLE_SET_KEY);
        List<CurrentChatState> result = new ArrayList<>(chatIds.size());
        for (String chatIdStr : chatIds) {
            long chatId = Long.parseLong(chatIdStr);
            Map<String, String> fields = redis.hgetAll(chatViewKey(chatId));
            if (fields.isEmpty()) {

                redis.srem(REFRESHABLE_SET_KEY, chatIdStr);
                continue;
            }
            result.add(toState(chatId, fields));
        }
        return result;
    }

    private static CurrentChatState toState(long chatId, Map<String, String> fields) {
        return new CurrentChatState(
                chatId,
                ChatState.valueOf(fields.get("state")),
                Integer.parseInt(fields.get("messageId")),
                fields.get("username"));
    }

    private static String chatViewKey(long chatId) {
        return "chatview:" + chatId;
    }
}
