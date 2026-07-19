package academy.backend.pollbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AppConfig(Bot bot, Redis redis, Scheduler scheduler) {

    public record Bot(String token) {
    }

    // port is a String, not an int: placeholders are resolved after YAML parsing (see
    // AbstractYamlConfigLoader), so a field that might hold "${REDIS_PORT:6379}" at parse time
    // has to be a type Jackson can always bind that text into.
    public record Redis(String host, String port, @JsonProperty("ttl-days") int ttlDays) {
    }

    public record Scheduler(@JsonProperty("refresh-interval-seconds") int refreshIntervalSeconds) {
    }
}
