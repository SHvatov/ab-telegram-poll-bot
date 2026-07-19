package academy.backend.pollbot.config;

import academy.backend.pollbot.loader.AbstractYamlConfigLoader;

public final class MemesConfigLoader extends AbstractYamlConfigLoader<MemesConfig> {

    public MemesConfigLoader() {
        super("/memes.yml", MemesConfig.class);
    }
}
