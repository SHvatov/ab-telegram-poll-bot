package academy.backend.pollbot.config.i18n;

import academy.backend.pollbot.loader.AbstractYamlConfigLoader;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class LocalizationLoader extends AbstractYamlConfigLoader<Map<String, String>> {

    public LocalizationLoader() {
        super("/i18n/ru.yml", null);
    }

    @Override
    protected Map<String, String> parse(String yaml) throws IOException {
        JsonNode root = YAML_MAPPER.readTree(yaml);
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
