package academy.backend.pollbot.domain;

public enum Rating {
    S(0),
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
