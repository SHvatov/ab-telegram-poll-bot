package academy.backend.pollbot.config;

import academy.backend.pollbot.domain.MemeDefinition;

import java.util.List;

/** Mirrors the structure of {@code memes.yml} 1:1. Pure data, no behavior. */
public record MemesConfig(List<MemeDefinition> memes) {

    public MemesConfig {
        memes = List.copyOf(memes);
    }
}
