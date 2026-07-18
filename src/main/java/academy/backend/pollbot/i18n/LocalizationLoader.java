package academy.backend.pollbot.i18n;

import academy.backend.pollbot.loader.AbstractYamlConfigLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Flattens the nested {@code i18n/ru.yml} tree into a "dot.path" -> text map. */
public final class LocalizationLoader extends AbstractYamlConfigLoader<Map<String, String>> {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public LocalizationLoader() {
        super("/i18n/ru.yml");
    }

    @Override
    protected Map<String, String> parse(String yaml) throws IOException {
        JsonNode root = mapper.readTree(yaml);
        Map<String, String> flat = new HashMap<>();
        flatten("", root, flat);
        return Map.copyOf(flat);
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
