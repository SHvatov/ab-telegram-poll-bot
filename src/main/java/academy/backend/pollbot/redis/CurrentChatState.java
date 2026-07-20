package academy.backend.pollbot.redis;

import academy.backend.pollbot.domain.ChatState;

public record CurrentChatState(long chatId, ChatState state, int messageId, String username) {
}
