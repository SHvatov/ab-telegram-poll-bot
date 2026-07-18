package academy.backend.pollbot.config;

import academy.backend.pollbot.loader.AbstractYamlConfigLoader;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;

public final class AppConfigLoader extends AbstractYamlConfigLoader<AppConfig> {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public AppConfigLoader() {
        super("/application.yml");
    }

    @Override
    protected AppConfig parse(String yaml) throws IOException {
        return mapper.readValue(yaml, AppConfig.class);
    }
}
