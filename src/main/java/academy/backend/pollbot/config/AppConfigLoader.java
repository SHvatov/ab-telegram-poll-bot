package academy.backend.pollbot.config;

import academy.backend.pollbot.loader.AbstractYamlConfigLoader;

public final class AppConfigLoader extends AbstractYamlConfigLoader<AppConfig> {

    public AppConfigLoader() {
        super("/application.yml", AppConfig.class);
    }
}
