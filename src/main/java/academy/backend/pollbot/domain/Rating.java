package academy.backend.pollbot.domain;

/**
 * User rating for a meme, best to worst. {@link #rank} is used to break ties
 * when computing the global (most frequent) rating: on a tie between two
 * ratings with equal vote counts, the better one (lower rank) wins.
 */
public enum Rating {
    Z(0, "vote.rate.button.z"),
    A(1, "vote.rate.button.a"),
    B(2, "vote.rate.button.b"),
    C(3, "vote.rate.button.c"),
    F(4, "vote.rate.button.f");

    private final int rank;
    private final String buttonLocalizationKey;

    Rating(int rank, String buttonLocalizationKey) {
        this.rank = rank;
        this.buttonLocalizationKey = buttonLocalizationKey;
    }

    public int rank() {
        return rank;
    }

    public String buttonLocalizationKey() {
        return buttonLocalizationKey;
    }
}
