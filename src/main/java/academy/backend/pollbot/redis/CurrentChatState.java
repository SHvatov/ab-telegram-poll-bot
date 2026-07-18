package academy.backend.pollbot.redis;

import academy.backend.pollbot.domain.ChatState;

/**
 * What a chat's single evolving bot message currently shows, as tracked in Redis. Read on every
 * navigation action to decide whether to edit that message in place or delete-and-resend, and
 * scanned by the background scheduler to find open lists that need periodic refreshing.
 *
 * @param chatId    the Telegram chat this message was sent to
 * @param state     which screen is currently rendered
 * @param messageId the id of the currently shown message, to edit or delete it
 * @param username  the chat's registered username, needed to re-render the screen (e.g. to look
 *                  up that user's own votes) without a round trip back through the update itself
 */
public record CurrentChatState(long chatId, ChatState state, int messageId, String username) {
}
