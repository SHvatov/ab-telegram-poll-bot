package academy.backend.pollbot.i18n;

import java.util.Map;

/** Resolves {@code {placeholder}} tokens against a flat "dot.path" -> text map loaded once at startup. */
public final class Localization {

    private final Map<String, String> messages;

    public Localization(Map<String, String> messages) {
        this.messages = messages;
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
}
