package io.github.trae.di.resolvers.interfaces;

import java.nio.file.Path;

public interface IConfigurationResolver {

    void resolve(final Class<?> type, final Path configurationDirectory);
}