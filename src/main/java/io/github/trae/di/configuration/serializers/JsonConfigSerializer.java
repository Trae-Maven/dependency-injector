package io.github.trae.di.configuration.serializers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * {@link ConfigSerializer} implementation using Gson for JSON format.
 *
 * <p>Produces pretty-printed JSON with HTML escaping disabled.</p>
 */
public class JsonConfigSerializer implements ConfigSerializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    @Override
    public <T> T deserialize(final String content, final Class<T> type) {
        return GSON.fromJson(content, type);
    }

    @Override
    public String serialize(final Object instance) {
        return GSON.toJson(instance);
    }
}
