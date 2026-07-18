package academy.backend.pollbot.config;

import academy.backend.pollbot.loader.AbstractYamlConfigLoader;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;

public final class MemesConfigLoader extends AbstractYamlConfigLoader<MemesConfig> {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public MemesConfigLoader() {
        super("/memes.yml");
    }

    @Override
    protected MemesConfig parse(String yaml) throws IOException {
        return mapper.readValue(yaml, MemesConfig.class);
    }
}
