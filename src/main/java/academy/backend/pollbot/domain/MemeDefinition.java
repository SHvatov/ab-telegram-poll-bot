package academy.backend.pollbot.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/** A single meme catalog entry, as loaded from {@code memes.yml}. Pure data, no behavior. */
public record MemeDefinition(
        int position,
        String code,
        String name,
        String path,
        String description,
        @JsonProperty("available-after") OffsetDateTime availableAfter
) {
}
