package academy.backend.pollbot.domain;

/**
 * User rating for a meme, best to worst. {@link #rank} is used to break ties
 * when computing the global (most frequent) rating: on a tie between two
 * ratings with equal vote counts, the better one (lower rank) wins.
 */
public enum Rating {
    Z(0),
    A(1),
    B(2),
    C(3),
    F(4);

    private final int rank;

    Rating(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
