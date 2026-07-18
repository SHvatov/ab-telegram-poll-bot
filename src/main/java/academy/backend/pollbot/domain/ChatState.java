package academy.backend.pollbot.domain;

public enum ChatState {

    MENU(true, false),

    CHOOSING_MEME(true, true),

    RATING_MEME(false, false),

    WATCHING_MEME_RATINGS(true, true),

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
