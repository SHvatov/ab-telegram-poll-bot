package academy.backend.pollbot.loader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractYamlConfigLoader<T> {

    // Whole-string match only (^...$): a placeholder is resolved after parsing, against an
    // already-typed field value, never by splicing text into the raw YAML before it is parsed.
    // That's what keeps an env var value from being able to inject extra YAML structure - it can
    // only ever become the literal content of the one String field it was found in.
    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{([A-Za-z0-9_]+)(:(.*))?}$");

    protected static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final String resourcePath;
    private final Class<T> type;

    protected AbstractYamlConfigLoader(String resourcePath, Class<T> type) {
        this.resourcePath = resourcePath;
        this.type = type;
    }

    public final T load() {
        try {
            return resolvePlaceholders(parse(readResourceText()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse " + resourcePath, e);
        }
    }

    protected T parse(String yaml) throws IOException {
        return YAML_MAPPER.readValue(yaml, type);
    }

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

    @SuppressWarnings("unchecked")
    private static <R> R resolvePlaceholders(R value) {
        if (value instanceof String s) {
            return (R) resolvePlaceholder(s);
        }
        if (value == null || !value.getClass().isRecord()) {
            return value;
        }
        RecordComponent[] components = value.getClass().getRecordComponents();
        Object[] args = new Object[components.length];
        try {
            for (int i = 0; i < components.length; i++) {
                args[i] = resolvePlaceholders(components[i].getAccessor().invoke(value));
            }
            Constructor<R> constructor = (Constructor<R>) value.getClass().getDeclaredConstructor(
                    Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new));
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to resolve placeholders in " + value.getClass(), e);
        }
    }

    private static String resolvePlaceholder(String value) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        if (!matcher.matches()) {
            return value;
        }
        String envVar = matcher.group(1);
        String defaultValue = matcher.group(3);
        String resolved = System.getenv(envVar);
        if (resolved != null) {
            return resolved;
        }
        if (defaultValue != null) {
            return defaultValue;
        }
        throw new IllegalStateException(
                "Environment variable " + envVar + " is required (config placeholder ${" + envVar + "}) but is not set");
    }
}
