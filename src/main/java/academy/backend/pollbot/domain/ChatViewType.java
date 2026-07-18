package academy.backend.pollbot.domain;

/**
 * What is currently rendered in a chat's single evolving bot message.
 * MENU/VOTE_LIST/RATING_LIST/NOT_IMPLEMENTED are plain text messages that can
 * be edited in place. MEME_DETAIL is a photo message with a caption, which
 * Telegram does not allow converting a text message into (or vice versa) via
 * an edit - transitions to/from it always delete the old message and send a
 * new one.
 */
public enum ChatViewType {
    MENU,
    VOTE_LIST,
    RATING_LIST,
    NOT_IMPLEMENTED,
    MEME_DETAIL;

    public boolean isTextView() {
        return this != MEME_DETAIL;
    }

    public boolean isRefreshableList() {
        return this == VOTE_LIST || this == RATING_LIST;
    }
}
