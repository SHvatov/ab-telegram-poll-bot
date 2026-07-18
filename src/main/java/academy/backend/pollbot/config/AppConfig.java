package academy.backend.pollbot.config;

public record AppConfig(
        String botToken,
        String redisHost,
        int redisPort,
        int refreshIntervalSeconds,
        long userDataTtlSeconds
) {
}
