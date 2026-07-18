package academy.backend.pollbot.repository;

import academy.backend.pollbot.domain.ChatViewType;
import academy.backend.pollbot.redis.ChatViewState;
import academy.backend.pollbot.redis.RedisKeys;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tracks which view (and Telegram message) is currently shown in each chat, so that
 * navigation knows whether to edit the existing message or send a new one, and so the
 * background scheduler knows which open list messages to refresh (and for whom).
 */
public final class ChatViewRepository {

    private final UnifiedJedis redis;

    public ChatViewRepository(UnifiedJedis redis) {
        this.redis = redis;
    }

    public void setView(long chatId, ChatViewType type, int messageId, String username) {
        String key = RedisKeys.chatView(chatId);
        redis.hset(key, Map.of(
                "type", type.name(),
                "messageId", String.valueOf(messageId),
                "username", username));
        redis.expire(key, RedisKeys.CHAT_VIEW_TTL_SECONDS);
    }

    public Optional<ChatViewState> getView(long chatId) {
        Map<String, String> fields = redis.hgetAll(RedisKeys.chatView(chatId));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toState(chatId, fields));
    }

    /** All chats currently showing a live-refreshable list (vote or rating), found via SCAN. */
    public List<ChatViewState> listRefreshableViews() {
        List<ChatViewState> result = new ArrayList<>();
        ScanParams params = new ScanParams().match(RedisKeys.CHAT_VIEW_SCAN_PATTERN).count(200);
        String cursor = ScanParams.SCAN_POINTER_START;
        do {
            ScanResult<String> scanResult = redis.scan(cursor, params);
            cursor = scanResult.getCursor();
            for (String key : scanResult.getResult()) {
                Map<String, String> fields = redis.hgetAll(key);
                if (fields.isEmpty()) {
                    continue;
                }
                ChatViewState state = toState(RedisKeys.chatIdFromViewKey(key), fields);
                if (state.type().isRefreshableList()) {
                    result.add(state);
                }
            }
        } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
        return result;
    }

    private static ChatViewState toState(long chatId, Map<String, String> fields) {
        return new ChatViewState(
                chatId,
                ChatViewType.valueOf(fields.get("type")),
                Integer.parseInt(fields.get("messageId")),
                fields.get("username"));
    }
}
