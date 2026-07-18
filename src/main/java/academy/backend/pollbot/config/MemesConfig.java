package academy.backend.pollbot.config;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record MemesConfig(List<MemeDefinition> memes) {

    public MemesConfig {
        memes = List.copyOf(memes);
    }

    public Optional<MemeDefinition> findByCode(String code) {
        return memes.stream().filter(m -> m.code().equals(code)).findFirst();
    }

    public List<MemeDefinition> availableAsOf(OffsetDateTime now) {
        return memes.stream()
                .filter(m -> m.isAvailable(now))
                .sorted(Comparator.comparingInt(MemeDefinition::position))
                .toList();
    }
}
