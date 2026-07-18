package academy.backend.pollbot.redis;

import academy.backend.pollbot.domain.ChatViewType;

public record ChatViewState(long chatId, ChatViewType type, int messageId, String username) {
}
