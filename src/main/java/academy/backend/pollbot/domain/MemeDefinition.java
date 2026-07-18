package academy.backend.pollbot.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record MemeDefinition(
        int position,
        String code,
        String name,
        String path,
        String description,
        @JsonProperty("available-after") OffsetDateTime availableAfter
) {
}
