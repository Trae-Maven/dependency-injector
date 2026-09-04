package io.github.trae.di.resolvers.interfaces;

import java.nio.file.Path;

/**
 * Loads configuration classes from disk and registers their instances
 * into the container.
 */
public interface IConfigurationResolver {

    /**
     * Loads or creates the configuration instance for the given class and
     * registers it into the container.
     *
     * @param type                   the configuration-annotated class
     * @param configurationDirectory the directory the config file is stored in
     */
    void resolve(final Class<?> type, final Path configurationDirectory);
}