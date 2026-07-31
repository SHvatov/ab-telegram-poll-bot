package academy.backend.pollbot.telegram.routing;

import academy.backend.pollbot.domain.Rating;

import java.util.Optional;

/**
 * Encodes/decodes the small string protocol carried in inline keyboard callback_data (Telegram
 * caps this at 64 bytes). Every code below is an arbitrary short token, not a readable name for
 * its action - callback_data is visible to whatever client sends it, so nothing here should hint
 * at what a button does or which meme it targets. Meme references carry {@link
 * academy.backend.pollbot.domain.MemeManager#token} rather than the meme's real code for the
 * same reason.
 */
public final class CallbackProtocol {

    public static final String MAIN_MENU = "q7k";
    public static final String SHOW_VOTE_LIST = "j2m";
    public static final String SHOW_RATING_LIST = "t9p";
    public static final String MY_TIER_LIST = "v4d";
    public static final String GLOBAL_TIER_LIST = "h6s";
    public static final String SOURCE = "n1w";
    public static final String VOTE_LIST_BACK = "c8y";
    public static final String RATING_LIST_BACK = "f3g";
    public static final String ADMIN_PICK_WINNER = "a1e";
    public static final String ADMIN_STATS = "a2r";

    private static final String OPEN_MEME_FOR_VOTE_PREFIX = "z5r:";
    private static final String OPEN_MEME_FOR_RATING_PREFIX = "k0b:";
    private static final String SUBMIT_VOTE_PREFIX = "x7q:";

    private CallbackProtocol() {
    }

    public static String openMemeForVote(String memeToken) {
        return OPEN_MEME_FOR_VOTE_PREFIX + memeToken;
    }

    public static String openMemeForRating(String memeToken) {
        return OPEN_MEME_FOR_RATING_PREFIX + memeToken;
    }

    public static String submitVote(String memeToken, Rating rating) {
        return SUBMIT_VOTE_PREFIX + memeToken + ":" + rating.name();
    }

    public static Optional<String> parseOpenMemeForVote(String data) {
        return data.startsWith(OPEN_MEME_FOR_VOTE_PREFIX)
                ? Optional.of(data.substring(OPEN_MEME_FOR_VOTE_PREFIX.length()))
                : Optional.empty();
    }

    public static Optional<String> parseOpenMemeForRating(String data) {
        return data.startsWith(OPEN_MEME_FOR_RATING_PREFIX)
                ? Optional.of(data.substring(OPEN_MEME_FOR_RATING_PREFIX.length()))
                : Optional.empty();
    }

    public static Optional<SubmitVote> parseSubmitVote(String data) {
        if (!data.startsWith(SUBMIT_VOTE_PREFIX)) {
            return Optional.empty();
        }
        String rest = data.substring(SUBMIT_VOTE_PREFIX.length());
        int separator = rest.lastIndexOf(':');
        if (separator < 0) {
            return Optional.empty();
        }
        String memeToken = rest.substring(0, separator);
        try {
            Rating rating = Rating.valueOf(rest.substring(separator + 1));
            return Optional.of(new SubmitVote(memeToken, rating));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record SubmitVote(String memeToken, Rating rating) {
    }
}
