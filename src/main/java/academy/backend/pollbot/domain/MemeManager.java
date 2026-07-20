package academy.backend.pollbot.domain;

import academy.backend.pollbot.config.MemesConfig;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class MemeManager {

    private final List<MemeDefinition> memes;
    private final Clock clock;
    private final Map<String, MemeDefinition> byToken;

    public MemeManager(MemesConfig memesConfig, Clock clock) {
        memesConfig.memes().forEach(MemeManager::validatePath);
        this.memes = memesConfig.memes();
        this.clock = clock;
        this.byToken = memes.stream().collect(Collectors.toMap(MemeManager::token, m -> m));
    }

    public int count() {
        return memes.size();
    }

    /**
     * The opaque, non-reversible-at-a-glance reference to a meme used in Telegram callback_data,
     * so button payloads never expose the meme's real (human-readable) code. Stable across
     * restarts since it's derived purely from the code itself.
     */
    public static String token(MemeDefinition meme) {
        return Integer.toHexString(meme.code().hashCode());
    }

    public Optional<MemeDefinition> findByToken(String token) {
        return Optional.ofNullable(byToken.get(token));
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

    private static void validatePath(MemeDefinition meme) {
        String path = meme.path();
        if (path.contains("..") || path.startsWith("/")) {
            throw new IllegalStateException("Meme '" + meme.code() + "' has an unsafe resource path: " + path);
        }
    }
}
