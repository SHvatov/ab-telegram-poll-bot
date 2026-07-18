package academy.backend.pollbot.loader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractYamlConfigLoader<T> {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_]+)(:([^}]*))?}");

    private final String resourcePath;

    protected AbstractYamlConfigLoader(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public final T load() {
        String yaml = resolvePlaceholders(readResourceText());
        try {
            return parse(yaml);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse " + resourcePath, e);
        }
    }

    protected abstract T parse(String yaml) throws IOException;

    private String readResourceText() {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found on classpath: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resourcePath, e);
        }
    }

    private static String resolvePlaceholders(String yaml) {
        Matcher matcher = PLACEHOLDER.matcher(yaml);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String envVar = matcher.group(1);
            String defaultValue = matcher.group(3);
            String value = System.getenv(envVar);
            if (value == null) {
                value = defaultValue;
            }
            if (value == null) {
                throw new IllegalStateException(
                        "Environment variable " + envVar + " is required (config placeholder ${"
                                + envVar + "}) but is not set");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
