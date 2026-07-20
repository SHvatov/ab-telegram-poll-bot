package academy.backend.pollbot.domain;

import academy.backend.pollbot.config.MemesConfig;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class MemeManager {

    private final List<MemeDefinition> memes;
    private final Clock clock;

    public MemeManager(MemesConfig memesConfig, Clock clock) {
        this.memes = memesConfig.memes();
        this.clock = clock;
    }

    public int count() {
        return memes.size();
    }

    public Optional<MemeDefinition> findByCode(String code) {
        return memes.stream().filter(m -> m.code().equals(code)).findFirst();
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
}
