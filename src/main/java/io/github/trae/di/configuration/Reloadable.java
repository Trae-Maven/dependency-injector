package io.github.trae.di.configuration;

import io.github.trae.di.configuration.annotations.Configuration;

/**
 * Marker interface for {@link Configuration @Configuration} POJOs that
 * support being reloaded from disk at runtime.
 *
 * <p>Configurations implementing this interface are re-read from their
 * backing file and have their fields repopulated in place, so existing
 * injected references stay valid. Configurations that do not implement
 * it are loaded once at startup and left untouched thereafter.</p>
 *
 * <p>This interface is exclusive to the configuration module and carries
 * no meaning for regular container-managed components.</p>
 */
public interface Reloadable {
}