package academy.backend.pollbot.domain;

import academy.backend.pollbot.config.MemesConfig;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class MemeManager {

    private final List<MemeDefinition> memes;

    public MemeManager(MemesConfig memesConfig) {
        this.memes = memesConfig.memes();
    }

    public int count() {
        return memes.size();
    }

    public Optional<MemeDefinition> findByCode(String code) {
        return memes.stream().filter(m -> m.code().equals(code)).findFirst();
    }

    public boolean isAvailable(MemeDefinition meme, OffsetDateTime now) {
        return !now.isBefore(meme.availableAfter());
    }

    public List<MemeDefinition> availableAsOf(OffsetDateTime now) {
        return memes.stream()
                .filter(m -> isAvailable(m, now))
                .sorted(Comparator.comparingInt(MemeDefinition::position))
                .toList();
    }
}
