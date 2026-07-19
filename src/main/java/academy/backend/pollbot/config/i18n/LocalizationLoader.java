package academy.backend.pollbot.config.i18n;

import academy.backend.pollbot.loader.AbstractYamlConfigLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;

import java.io.IOException;
import java.util.Map;

public final class LocalizationLoader extends AbstractYamlConfigLoader<Map<String, String>> {

    private static final JavaPropsMapper PROPS_MAPPER = new JavaPropsMapper();

    public LocalizationLoader() {
        super("/i18n/ru.yml", null);
    }

    @Override
    protected Map<String, String> parse(String yaml) throws IOException {
        JsonNode root = YAML_MAPPER.readTree(yaml);
        return PROPS_MAPPER.writeValueAsMap(root);
    }
}
