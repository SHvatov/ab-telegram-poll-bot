package academy.backend.pollbot.i18n;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Loads a flat "dot.path" -> text map from a YAML resource once at startup and
 * resolves {placeholder} tokens on lookup.
 */
public final class Localization {

    private static final String RESOURCE_PATH = "/i18n/ru.yml";

    private final Map<String, String> messages;

    public Localization() {
        this.messages = load();
    }

    public String get(String key) {
        return get(key, Map.of());
    }

    public String get(String key, Map<String, Object> params) {
        String template = messages.get(key);
        if (template == null) {
            throw new IllegalStateException("Missing localization key: " + key);
        }
        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private static Map<String, String> load() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (InputStream in = Localization.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found on classpath: " + RESOURCE_PATH);
            }
            JsonNode root = mapper.readTree(in);
            Map<String, String> flat = new HashMap<>();
            flatten("", root, flat);
            return Map.copyOf(flat);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + RESOURCE_PATH, e);
        }
    }

    private static void flatten(String prefix, JsonNode node, Map<String, String> out) {
        if (node.isValueNode()) {
            out.put(prefix, node.asText());
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
            flatten(key, field.getValue(), out);
        }
    }
}
