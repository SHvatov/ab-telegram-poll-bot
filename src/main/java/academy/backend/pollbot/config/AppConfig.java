package academy.backend.pollbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AppConfig(Bot bot, Redis redis, Scheduler scheduler) {

    public record Bot(String token) {
    }

    public record Redis(String host, int port, @JsonProperty("ttl-days") int ttlDays) {
    }

    public record Scheduler(@JsonProperty("refresh-interval-seconds") int refreshIntervalSeconds) {
    }
}
