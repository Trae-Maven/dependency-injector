package io.github.trae.di.configuration.serializers;

/**
 * Abstracts serialization and deserialization of configuration POJOs
 * to and from a specific file format (e.g. JSON, YAML).
 *
 * <p>Implementations must be stateless and thread-safe.</p>
 */
public interface ConfigSerializer {

    /**
     * Deserializes the given string content into an instance of the specified type.
     *
     * @param content the file content to deserialize
     * @param type    the target class
     * @param <T>     the target type
     * @return the deserialized instance
     */
    <T> T deserialize(final String content, final Class<T> type);

    /**
     * Serializes the given instance into a formatted string suitable for
     * writing to a file.
     *
     * @param instance the configuration instance to serialize
     * @return the serialized string
     */
    String serialize(final Object instance);
}
