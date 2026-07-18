package academy.backend.pollbot.domain;

/**
 * What is currently rendered in a chat's single evolving bot message.
 * <p>
 * {@code text} states (MENU, CHOOSING_MEME, WATCHING_MEME_RATINGS) can be edited in place;
 * {@code photo} states (RATING_MEME, WATCHING_MEME_RATING) are a meme image with a caption,
 * which Telegram does not allow converting a text message into (or vice versa) via an edit -
 * transitions to/from a photo state always delete the old message and send a new one.
 * <p>
 * Only {@code refreshable} states are periodically re-rendered by the background scheduler.
 */
public enum ChatState {
    /** The main menu. */
    MENU(true, false),
    /** Picking a meme to vote on, from the list of currently available ones. */
    CHOOSING_MEME(true, true),
    /** Looking at one meme's photo in the voting flow: either rate it, or see your own rating. */
    RATING_MEME(false, false),
    /** The read-only global ratings list for every currently available meme. */
    WATCHING_MEME_RATINGS(true, true),
    /** Looking at one meme's photo read-only, with its global rating. */
    WATCHING_MEME_RATING(false, false);

    private final boolean text;
    private final boolean refreshable;

    ChatState(boolean text, boolean refreshable) {
        this.text = text;
        this.refreshable = refreshable;
    }

    public boolean isText() {
        return text;
    }

    public boolean isRefreshable() {
        return refreshable;
    }
}
