package academy.backend.pollbot.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public final class MemesConfigLoader {

    private static final String RESOURCE_PATH = "/memes.yml";

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public MemesConfig load() {
        try (InputStream in = MemesConfigLoader.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found on classpath: " + RESOURCE_PATH);
            }
            return mapper.readValue(in, MemesConfig.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + RESOURCE_PATH, e);
        }
    }
}
