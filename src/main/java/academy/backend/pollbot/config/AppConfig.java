package academy.backend.pollbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AppConfig(Bot bot, Redis redis, Scheduler scheduler, Data data) {

    public record Bot(String token) {
    }

    public record Redis(String host, int port) {
    }

    public record Scheduler(@JsonProperty("refresh-interval-seconds") int refreshIntervalSeconds) {
    }

    public record Data(@JsonProperty("ttl-days") int ttlDays) {
    }
}
