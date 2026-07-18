package academy.backend.pollbot.redis;

public final class RedisKeys {

    public static final String CHAT_VIEW_SCAN_PATTERN = "chatview:*";

    /** How long an untouched chat-view pointer survives before the scheduler stops refreshing it. */
    public static final long CHAT_VIEW_TTL_SECONDS = 60 * 60; // 1 hour

    private RedisKeys() {
    }

    public static String user(String username) {
        return "user:" + username;
    }

    public static String userVotes(String username) {
        return "user:" + username + ":votes";
    }

    public static String memeVotes(String memeCode) {
        return "meme:" + memeCode + ":votes";
    }

    public static String chatView(long chatId) {
        return "chatview:" + chatId;
    }

    public static long chatIdFromViewKey(String key) {
        return Long.parseLong(key.substring("chatview:".length()));
    }
}
