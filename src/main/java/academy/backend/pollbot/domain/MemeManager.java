package academy.backend.pollbot.domain;

import academy.backend.pollbot.config.MemesConfig;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MemeManager {

    private static final int MIX_MULTIPLIER = 0x9E3779B1; // odd -> multiplication mod 2^32 is a bijection

    private final List<MemeDefinition> memes;
    private final Clock clock;
    private final Map<String, MemeDefinition> byToken;

    public MemeManager(MemesConfig memesConfig, Clock clock) {
        memesConfig.memes().forEach(MemeManager::validatePath);
        this.memes = memesConfig.memes();
        this.clock = clock;
        this.byToken = buildTokenIndex(memes);
    }

    public int count() {
        return memes.size();
    }

    /**
     * The opaque, non-reversible-at-a-glance reference to a meme used in Telegram callback_data,
     * so button payloads never expose the meme's real (human-readable) code. Stable across
     * restarts since it's derived purely from the meme's position. Uses a bijective bit-mix
     * rather than {@code String.hashCode()} so distinct memes can never collide onto the same
     * token - a lossy hash could, silently or with a confusing crash at startup.
     */
    public static String token(MemeDefinition meme) {
        return String.format("%08x", mix(meme.position()));
    }

    public Optional<MemeDefinition> findByToken(String token) {
        return Optional.ofNullable(byToken.get(token));
    }

    private static int mix(int position) {
        int x = position * MIX_MULTIPLIER;
        x ^= x >>> 16;
        x *= MIX_MULTIPLIER;
        x ^= x >>> 16;
        return x;
    }

    public boolean isAvailable(MemeDefinition meme) {
        return !OffsetDateTime.now(clock).isBefore(meme.availableAfter());
    }

    public List<MemeDefinition> availableAsOf() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return memes.stream()
                .filter(m -> !now.isBefore(m.availableAfter()))
                .sorted(Comparator.comparingInt(MemeDefinition::position))
                .toList();
    }

    private static Map<String, MemeDefinition> buildTokenIndex(List<MemeDefinition> memes) {
        Map<String, MemeDefinition> index = new HashMap<>();
        for (MemeDefinition meme : memes) {
            MemeDefinition existing = index.put(token(meme), meme);
            if (existing != null) {
                throw new IllegalStateException("Memes '" + existing.code() + "' and '" + meme.code()
                        + "' share a token - they must have distinct positions");
            }
        }
        return index;
    }

    private static void validatePath(MemeDefinition meme) {
        String path = meme.path();
        if (path.contains("..") || path.startsWith("/")) {
            throw new IllegalStateException("Meme '" + meme.code() + "' has an unsafe resource path: " + path);
        }
    }
}
