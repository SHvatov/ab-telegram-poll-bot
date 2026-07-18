package academy.backend.pollbot.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;

public final class AppConfigLoader {

    private static final String RESOURCE_PATH = "/application.yml";
    private static final String BOT_TOKEN_ENV = "TELEGRAM_BOT_TOKEN";

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public AppConfig load() {
        RawConfig raw = readRawConfig();

        String botToken = System.getenv(BOT_TOKEN_ENV);
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalStateException(
                    "Environment variable " + BOT_TOKEN_ENV + " is not set. It must contain the Telegram bot token.");
        }

        String redisHost = envOr("REDIS_HOST", raw.redis().host());
        int redisPort = envIntOr("REDIS_PORT", raw.redis().port());

        long ttlSeconds = TimeUnit.DAYS.toSeconds(raw.data().ttlDays());

        return new AppConfig(botToken, redisHost, redisPort, raw.scheduler().refreshIntervalSeconds(), ttlSeconds);
    }

    private RawConfig readRawConfig() {
        try (InputStream in = AppConfigLoader.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found on classpath: " + RESOURCE_PATH);
            }
            return mapper.readValue(in, RawConfig.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + RESOURCE_PATH, e);
        }
    }

    private static String envOr(String envVar, String fallback) {
        String value = System.getenv(envVar);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static int envIntOr(String envVar, int fallback) {
        String value = System.getenv(envVar);
        return (value == null || value.isBlank()) ? fallback : Integer.parseInt(value);
    }

    private record RawConfig(RawRedis redis, RawScheduler scheduler, RawData data) {
    }

    private record RawRedis(String host, int port) {
    }

    private record RawScheduler(@JsonProperty("refresh-interval-seconds") int refreshIntervalSeconds) {
    }

    private record RawData(@JsonProperty("ttl-days") int ttlDays) {
    }
}
