package io.github.trae.di.configuration.annotations;

import io.github.trae.di.configuration.enums.ConfigType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a configuration POJO managed by the DI container.
 *
 * <p>The framework scans for {@code @Configuration} classes, deserializes them
 * from the specified file format, and registers the instance into the container
 * for injection. If the file does not exist, it is created with default field values.</p>
 *
 * <p>No base class is required — any POJO works. Reload and save operations
 * are handled centrally via {@link io.github.trae.di.InjectorApi#reloadConfiguration(Class)},
 * {@link io.github.trae.di.InjectorApi#reloadConfigurations()}, and
 * {@link io.github.trae.di.InjectorApi#saveConfiguration(Class)}.</p>
 *
 * <p>Field names are used directly as keys. Static and transient fields are ignored.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Configuration {

    /**
     * The configuration file name (without extension).
     * The extension is determined by {@link #type()}.
     *
     * @return the config file name
     */
    String value();

    /**
     * The file format for this configuration. Defaults to {@link ConfigType#JSON}.
     *
     * @return the configuration file format
     */
    ConfigType type() default ConfigType.JSON;
}