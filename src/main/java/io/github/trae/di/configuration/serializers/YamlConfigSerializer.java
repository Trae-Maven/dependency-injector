package io.github.trae.di.configuration.serializers;

import io.github.trae.di.configuration.annotations.Comment;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.representer.Representer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link ConfigSerializer} implementation using SnakeYAML for YAML format.
 *
 * <p>Produces block-style YAML with 2-space indentation. Serialization
 * converts the instance to an ordered map via reflection to preserve
 * field declaration order and avoid the {@code !!} class type tag.</p>
 *
 * <p>Fields annotated with {@link Comment @Comment} have their comments
 * injected as {@code #} lines above the corresponding key in the output.</p>
 */
public class YamlConfigSerializer implements ConfigSerializer {

    private static final DumperOptions DUMPER_OPTIONS = createDumperOptions();

    /**
     * Creates the shared {@link DumperOptions} with block flow style
     * and 2-space indentation.
     *
     * @return the configured dumper options
     */
    private static DumperOptions createDumperOptions() {
        final DumperOptions options = new DumperOptions();

        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);

        return options;
    }

    /**
     * Deserializes the given YAML string into an instance of the specified type.
     *
     * <p>Uses field-based access so that getters and setters are not required.
     * Missing properties in the YAML content are silently skipped.</p>
     *
     * @param content the YAML string to deserialize
     * @param type    the target class
     * @param <T>     the target type
     * @return the deserialized instance
     */
    @Override
    public <T> T deserialize(final String content, final Class<T> type) {
        final Representer representer = new Representer(DUMPER_OPTIONS);
        representer.getPropertyUtils().setSkipMissingProperties(true);

        final Yaml yaml = new Yaml(new Constructor(type, new LoaderOptions()), representer, DUMPER_OPTIONS);
        yaml.setBeanAccess(BeanAccess.FIELD);

        return yaml.loadAs(content, type);
    }

    /**
     * Serializes the given instance into a YAML string with injected comments.
     *
     * <p>The instance is first converted to an ordered map via reflection
     * to preserve field declaration order and avoid the {@code !!} class
     * type tag. Comments from {@link Comment @Comment} annotations are
     * then injected above their corresponding keys.</p>
     *
     * @param instance the configuration instance to serialize
     * @return the serialized YAML string with comments
     */
    @Override
    public String serialize(final Object instance) {
        final LinkedHashMap<String, Object> map = toOrderedMap(instance);

        final Yaml yaml = new Yaml(DUMPER_OPTIONS);
        yaml.setBeanAccess(BeanAccess.FIELD);

        return injectComments(yaml.dump(map), instance.getClass());
    }

    /**
     * Converts the given instance to an ordered map by reading all declared
     * fields via reflection. Preserves the field declaration order so the
     * YAML output matches the class layout.
     *
     * @param instance the instance to convert
     * @return an ordered map of field names to their values
     */
    private static LinkedHashMap<String, Object> toOrderedMap(final Object instance) {
        final LinkedHashMap<String, Object> map = new LinkedHashMap<>();

        for (final Field field : instance.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            field.setAccessible(true);

            try {
                map.put(field.getName(), field.get(instance));
            } catch (final IllegalAccessException e) {
                throw new RuntimeException("Failed to access field: " + field.getName(), e);
            }
        }

        return map;
    }

    /**
     * Injects {@link Comment @Comment} annotations as {@code #} comment
     * lines above their corresponding YAML keys.
     *
     * @param yaml the serialized YAML string
     * @param type the configuration class to read annotations from
     * @return the YAML string with comments injected
     */
    private static String injectComments(final String yaml, final Class<?> type) {
        final Map<String, String[]> commentMap = buildCommentMap(type);

        if (commentMap.isEmpty()) {
            return yaml;
        }

        final StringBuilder result = new StringBuilder();

        for (final String line : yaml.split("\n")) {
            final String trimmed = line.trim();

            for (final Map.Entry<String, String[]> entry : commentMap.entrySet()) {
                if (trimmed.startsWith(entry.getKey() + ":")) {
                    final String indent = line.substring(0, line.indexOf(trimmed));

                    for (final String commentLine : entry.getValue()) {
                        result.append(indent)
                                .append("# ")
                                .append(commentLine)
                                .append("\n");
                    }
                    break;
                }
            }

            result.append(line).append("\n");
        }

        return result.toString();
    }

    /**
     * Builds a map of field names to their {@link Comment @Comment} values
     * from the given class.
     *
     * @param type the class to scan
     * @return ordered map of field name to comment lines
     */
    private static Map<String, String[]> buildCommentMap(final Class<?> type) {
        final Map<String, String[]> commentMap = new LinkedHashMap<>();

        final Field[] fields = type.getDeclaredFields();

        for (final Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            final Comment comment = field.getAnnotation(Comment.class);
            if (comment != null) {
                commentMap.put(field.getName(), comment.value());
            }
        }

        return commentMap;
    }
}