package academy.backend.pollbot.repository;

import academy.backend.pollbot.domain.ChatState;
import academy.backend.pollbot.redis.CurrentChatState;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tracks which screen (and Telegram message) is currently shown in each chat, so that
 * navigation knows whether to edit the existing message or send a new one, and so the
 * background scheduler knows which open lists to refresh (and for whom).
 */
public final class ChatViewRepository {

    private static final String SCAN_PATTERN = "chatview:*";

    /** How long an untouched chat-view pointer survives before the scheduler stops refreshing it. */
    private static final long VIEW_TTL_SECONDS = 60 * 60; // 1 hour

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
    }

    public Optional<CurrentChatState> getState(long chatId) {
        Map<String, String> fields = redis.hgetAll(chatViewKey(chatId));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toState(chatId, fields));
    }

    /** All chats currently showing a live-refreshable screen, found via SCAN. */
    public List<CurrentChatState> listRefreshableStates() {
        List<CurrentChatState> result = new ArrayList<>();
        ScanParams params = new ScanParams().match(SCAN_PATTERN).count(200);
        String cursor = ScanParams.SCAN_POINTER_START;
        do {
            ScanResult<String> scanResult = redis.scan(cursor, params);
            cursor = scanResult.getCursor();
            for (String key : scanResult.getResult()) {
                Map<String, String> fields = redis.hgetAll(key);
                if (fields.isEmpty()) {
                    continue;
                }
                CurrentChatState state = toState(chatIdFromKey(key), fields);
                if (state.state().isRefreshable()) {
                    result.add(state);
                }
            }
        } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
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

    private static long chatIdFromKey(String key) {
        return Long.parseLong(key.substring("chatview:".length()));
    }
}
