package academy.backend.pollbot.config;

import academy.backend.pollbot.domain.MemeDefinition;

import java.util.List;

public record MemesConfig(List<MemeDefinition> memes) {
}
