package io.github.trae.di.resolvers;

import io.github.trae.di.configuration.annotations.Configuration;
import io.github.trae.di.configuration.enums.ConfigType;
import io.github.trae.di.configuration.serializers.ConfigSerializer;
import io.github.trae.di.configuration.serializers.JsonConfigSerializer;
import io.github.trae.di.configuration.serializers.YamlConfigSerializer;
import io.github.trae.di.containers.ComponentContainer;
import io.github.trae.di.exceptions.InjectorException;
import io.github.trae.di.resolvers.abstracts.AbstractResolver;
import io.github.trae.di.resolvers.interfaces.IConfigurationResolver;
import io.github.trae.utilities.UtilField;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves {@link Configuration @Configuration}-annotated POJOs by loading
 * their instances from disk via the appropriate {@link ConfigSerializer},
 * and provides centralized {@link #reload(Class)} and {@link #reloadAll()}
 * functionality.
 *
 * <p>The file format (JSON or YAML) is determined by
 * {@link Configuration#type()} on each configuration class. A shared
 * {@link ConfigSerializer} instance per {@link ConfigType} handles all
 * serialization and deserialization.</p>
 *
 * <p>Configuration classes are plain POJOs — no base class required. The
 * resolver tracks the file path and serializer for each registered
 * configuration. Since the same object reference is retained in the DI
 * container, any component holding a reference to a config instance will
 * see updated values immediately after a reload.</p>
 *
 * <p>The resulting instance is registered into the {@link ComponentContainer}.</p>
 */
public class ConfigurationResolver extends AbstractResolver implements IConfigurationResolver {

    private static final Map<ConfigType, ConfigSerializer> SERIALIZER_MAP = new EnumMap<>(ConfigType.class);

    static {
        SERIALIZER_MAP.put(ConfigType.JSON, new JsonConfigSerializer());
        SERIALIZER_MAP.put(ConfigType.YAML, new YamlConfigSerializer());
    }

    private final Path configurationDirectory;

    /**
     * Maps each registered configuration class to its resolved file path.
     */
    private final Map<Class<?>, Path> filePathMap = new ConcurrentHashMap<>();

    /**
     * Maps each registered configuration class to its {@link ConfigType}.
     */
    private final Map<Class<?>, ConfigType> configTypeMap = new ConcurrentHashMap<>();

    /**
     * Creates a new {@link ConfigurationResolver}.
     *
     * @param componentContainer     the DI container to register instances into
     * @param configurationDirectory the directory where config files are stored
     */
    public ConfigurationResolver(final ComponentContainer componentContainer, final Path configurationDirectory) {
        super(componentContainer);

        if (configurationDirectory == null) {
            throw new IllegalArgumentException("Configuration Directory cannot be null.");
        }

        this.configurationDirectory = configurationDirectory;
    }

    /**
     * Returns an unmodifiable view of the configuration class to file path mappings.
     *
     * @return the file path map
     */
    public Map<Class<?>, Path> getFilePathMap() {
        return Collections.unmodifiableMap(this.filePathMap);
    }

    /**
     * Loads or creates a configuration instance for the given class and
     * registers it into the container.
     *
     * <p>The class must be annotated with {@link Configuration @Configuration}.
     * No base class is required — any POJO works. The file format and extension
     * are determined by {@link Configuration#type()}.</p>
     *
     * @param type the {@code @Configuration}-annotated class
     * @return the loaded or default instance
     * @throws InjectorException if the class is not annotated, or loading/saving fails
     */
    @Override
    public Object resolve(final Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null.");
        }

        final Configuration configuration = type.getAnnotation(Configuration.class);
        if (configuration == null) {
            throw new InjectorException("Class must be annotated with @%s: %s".formatted(Configuration.class.getSimpleName(), type.getName()));
        }

        final ConfigType configType = configuration.type();
        final Path filePath = this.configurationDirectory.resolve(configuration.value() + configType.getExtension());
        final ConfigSerializer serializer = getSerializer(configType);

        final Object instance = load(type, filePath, serializer);

        this.filePathMap.put(type, filePath);
        this.configTypeMap.put(type, configType);
        this.getComponentContainer().registerInstance(type, instance);

        return instance;
    }

    /**
     * Reloads a single configuration from disk by reading the file,
     * deserializing into a temporary instance, and copying all non-static,
     * non-transient field values into the existing container instance.
     *
     * <p>The same object reference is retained in the DI container, so any
     * component holding a reference to this config will see updated values
     * immediately after this call.</p>
     *
     * @param type the {@code @Configuration}-annotated class to reload
     * @throws InjectorException if the type is not registered, or reading fails
     */
    public void reload(final Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null.");
        }

        final Path filePath = this.filePathMap.get(type);
        if (filePath == null) {
            throw new InjectorException("Configuration not registered: %s".formatted(type.getName()));
        }

        if (!(Files.exists(filePath))) {
            throw new InjectorException("Configuration file does not exist: %s".formatted(filePath));
        }

        try {
            final String content = Files.readString(filePath);
            final ConfigSerializer serializer = getSerializer(this.configTypeMap.get(type));

            final Object loaded = serializer.deserialize(content, type);
            final Object existing = this.getComponentContainer().getInstance(type);

            copyFields(loaded, existing);
        } catch (final IOException e) {
            throw new InjectorException("Failed to reload configuration: %s".formatted(filePath), e);
        }
    }

    /**
     * Reloads all registered configurations from disk.
     *
     * @throws InjectorException if any configuration fails to reload
     */
    public void reloadAll() {
        for (final Class<?> type : this.filePathMap.keySet()) {
            reload(type);
        }
    }

    /**
     * Saves a configuration instance to disk using its registered format.
     *
     * @param type the {@code @Configuration}-annotated class to save
     * @throws InjectorException if the type is not registered, or writing fails
     */
    public void save(final Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null.");
        }

        final Path filePath = this.filePathMap.get(type);
        if (filePath == null) {
            throw new InjectorException("Configuration not registered: %s".formatted(type.getName()));
        }

        final Object instance = this.getComponentContainer().getInstance(type);
        final ConfigSerializer serializer = getSerializer(this.configTypeMap.get(type));

        save(instance, filePath, serializer);
    }

    /**
     * Returns the {@link ConfigSerializer} for the given {@link ConfigType}.
     *
     * @param configType the configuration format
     * @return the serializer
     * @throws InjectorException if no serializer is registered for the type
     */
    private static ConfigSerializer getSerializer(final ConfigType configType) {
        final ConfigSerializer serializer = SERIALIZER_MAP.get(configType);
        if (serializer == null) {
            throw new InjectorException("No serializer registered for config type: %s".formatted(configType));
        }

        return serializer;
    }

    /**
     * Loads a configuration instance from the given file path, or creates a default
     * instance and writes it to disk if the file does not exist.
     *
     * @param type       the configuration class
     * @param filePath   the path to the file
     * @param serializer the serializer to use
     * @return the loaded or default instance
     * @throws InjectorException if file I/O or instantiation fails
     */
    private Object load(final Class<?> type, final Path filePath, final ConfigSerializer serializer) {
        try {
            if (Files.exists(filePath)) {
                final String content = Files.readString(filePath);
                return serializer.deserialize(content, type);
            }

            final Object defaultInstance = type.getDeclaredConstructor().newInstance();

            save(defaultInstance, filePath, serializer);

            return defaultInstance;
        } catch (final IOException e) {
            throw new InjectorException("Failed to load configuration file: %s".formatted(filePath), e);
        } catch (final ReflectiveOperationException e) {
            throw new InjectorException("Failed to instantiate configuration class: %s".formatted(type.getName()), e);
        }
    }

    /**
     * Saves a configuration instance to the given file path.
     *
     * @param instance   the configuration instance to save
     * @param filePath   the path to write to
     * @param serializer the serializer to use
     * @throws InjectorException if file I/O fails
     */
    private void save(final Object instance, final Path filePath, final ConfigSerializer serializer) {
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, serializer.serialize(instance));
        } catch (final IOException e) {
            throw new InjectorException("Failed to save configuration file: %s".formatted(filePath), e);
        }
    }

    /**
     * Copies all non-static, non-transient field values from the source
     * instance to the target instance, walking up the class hierarchy
     * until {@link Object} is reached.
     *
     * @param source the instance to copy values from
     * @param target the instance to copy values into
     */
    private static void copyFields(final Object source, final Object target) {
        Class<?> clazz = source.getClass();

        while (clazz != null && clazz != Object.class) {
            for (final Field field : clazz.getDeclaredFields()) {
                final int modifiers = field.getModifiers();

                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                    continue;
                }

                field.setAccessible(true);

                try {
                    UtilField.set(field, target, field.get(source));
                } catch (final Exception e) {
                    throw new InjectorException("Failed to copy field: %s.%s".formatted(clazz.getSimpleName(), field.getName()), e);
                }
            }

            clazz = clazz.getSuperclass();
        }
    }
}
