package academy.backend.pollbot.config;

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

    public boolean isAvailable(OffsetDateTime now) {
        return !now.isBefore(availableAfter);
    }
}
