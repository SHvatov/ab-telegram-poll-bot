package academy.backend.pollbot.telegram.routing;

import academy.backend.pollbot.domain.Rating;

import java.util.Optional;

/**
 * Encodes/decodes the small string protocol carried in inline keyboard callback_data
 * (Telegram caps this at 64 bytes, hence the terse prefixes).
 */
public final class CallbackProtocol {

    public static final String MENU = "menu";
    public static final String MENU_VOTE = "menu:vote";
    public static final String MENU_RATING = "menu:rating";
    public static final String MENU_MY_TIER = "menu:mytier";
    public static final String MENU_GLOBAL_TIER = "menu:globaltier";
    public static final String VOTE_BACK = "vote:back";
    public static final String RATING_BACK = "rating:back";

    private static final String VOTE_OPEN_PREFIX = "vote:open:";
    private static final String RATING_OPEN_PREFIX = "rating:open:";
    private static final String VOTE_RATE_PREFIX = "vote:rate:";

    private CallbackProtocol() {
    }

    public static String voteOpen(String memeCode) {
        return VOTE_OPEN_PREFIX + memeCode;
    }

    public static String ratingOpen(String memeCode) {
        return RATING_OPEN_PREFIX + memeCode;
    }

    public static String voteRate(String memeCode, Rating rating) {
        return VOTE_RATE_PREFIX + memeCode + ":" + rating.name();
    }

    public static Optional<String> parseVoteOpen(String data) {
        return data.startsWith(VOTE_OPEN_PREFIX)
                ? Optional.of(data.substring(VOTE_OPEN_PREFIX.length()))
                : Optional.empty();
    }

    public static Optional<String> parseRatingOpen(String data) {
        return data.startsWith(RATING_OPEN_PREFIX)
                ? Optional.of(data.substring(RATING_OPEN_PREFIX.length()))
                : Optional.empty();
    }

    public static Optional<VoteRate> parseVoteRate(String data) {
        if (!data.startsWith(VOTE_RATE_PREFIX)) {
            return Optional.empty();
        }
        String rest = data.substring(VOTE_RATE_PREFIX.length());
        int separator = rest.lastIndexOf(':');
        if (separator < 0) {
            return Optional.empty();
        }
        String memeCode = rest.substring(0, separator);
        Rating rating = Rating.valueOf(rest.substring(separator + 1));
        return Optional.of(new VoteRate(memeCode, rating));
    }

    public record VoteRate(String memeCode, Rating rating) {
    }
}
