package io.github.trae.di.configuration.enums;

import io.github.trae.di.configuration.annotations.Configuration;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Supported configuration file formats.
 *
 * <p>Used in {@link Configuration#type()} to specify the serialization
 * format for a configuration POJO. The framework selects the appropriate
 * serializer and file extension based on this value.</p>
 */
@AllArgsConstructor
@Getter
public enum ConfigType {

    /**
     * JSON format using Gson. File extension: {@code .json}
     */
    JSON(".json"),

    /**
     * YAML format using SnakeYAML. File extension: {@code .yml}
     */
    YAML(".yml");

    private final String extension;
}
