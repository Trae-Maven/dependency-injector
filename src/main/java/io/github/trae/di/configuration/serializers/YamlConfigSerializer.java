package io.github.trae.di.configuration.serializers;

import io.github.trae.di.configuration.annotations.Comment;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.representer.Representer;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ConfigSerializer} implementation using SnakeYAML for YAML format.
 *
 * <p>Produces block-style YAML with 2-space indentation. Serialization
 * converts the instance to ordered maps via reflection, nested
 * configuration types included, to preserve field declaration order and
 * avoid the {@code !!} class type tag at every level.</p>
 *
 * <p>Fields annotated with {@link Comment @Comment} have their comments
 * injected as {@code #} lines above the corresponding key in the output,
 * including fields of nested configuration types.</p>
 */
public class YamlConfigSerializer implements ConfigSerializer {

    private static final DumperOptions DUMPER_OPTIONS = createDumperOptions();

    /**
     * Creates a serializer using the shared dumper options.
     */
    public YamlConfigSerializer() {
    }

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
     * <p>The instance is first converted to ordered maps via reflection
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
     * Converts the given instance to an ordered map by reading its fields
     * via reflection, walking the class hierarchy from the class itself up,
     * as Gson does. Static and transient fields are skipped.
     *
     * @param instance the instance to convert
     * @return an ordered map of field names to their converted values
     */
    private static LinkedHashMap<String, Object> toOrderedMap(final Object instance) {
        final LinkedHashMap<String, Object> map = new LinkedHashMap<>();

        Class<?> clazz = instance.getClass();
        while (clazz != null && clazz != Object.class) {
            for (final Field field : clazz.getDeclaredFields()) {
                final int modifiers = field.getModifiers();

                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                    continue;
                }

                field.setAccessible(true);

                try {
                    map.put(field.getName(), toYamlValue(field.get(instance)));
                } catch (final IllegalAccessException e) {
                    throw new RuntimeException("Failed to access field: " + field.getName(), e);
                }
            }
            clazz = clazz.getSuperclass();
        }

        return map;
    }

    /**
     * Converts a field value into something SnakeYAML writes without a class
     * tag.
     *
     * <p>Enums become their names, maps, collections and arrays are rebuilt
     * with their contents converted, and nested configuration types become
     * ordered maps. JDK values such as strings and numbers are written as they
     * are.</p>
     *
     * @param value the value to convert
     * @return the converted value
     */
    private static Object toYamlValue(final Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof final Enum<?> enumValue) {
            return enumValue.name();
        }

        if (value instanceof final Map<?, ?> map) {
            final LinkedHashMap<Object, Object> result = new LinkedHashMap<>();

            map.forEach((key, entryValue) -> result.put(toYamlValue(key), toYamlValue(entryValue)));

            return result;
        }

        if (value instanceof final Collection<?> collection) {
            final List<Object> result = new ArrayList<>();

            collection.forEach(element -> result.add(toYamlValue(element)));

            return result;
        }

        if (value.getClass().isArray()) {
            final List<Object> result = new ArrayList<>();

            for (int i = 0; i < Array.getLength(value); i++) {
                result.add(toYamlValue(Array.get(value, i)));
            }

            return result;
        }

        if (value.getClass().getName().startsWith("java.")) {
            return value;
        }

        return toOrderedMap(value);
    }

    /**
     * Injects {@link Comment @Comment} annotations as {@code #} comment
     * lines above their corresponding YAML keys.
     *
     * <p>Keys are matched by their full path from the root, such as
     * {@code delay.defaultValue}, rather than by name alone, so a nested
     * field never picks up the comment of a same-named field elsewhere.
     * The path is tracked by indentation: every key opens a frame at its
     * indent, and a line closes every frame at or beyond its own indent.
     * A list item opens a {@code null} frame, so keys inside list elements
     * never match.</p>
     *
     * @param yaml the serialized YAML string
     * @param type the configuration class to read annotations from
     * @return the YAML string with comments injected
     */
    private static String injectComments(final String yaml, final Class<?> type) {
        final Map<String, String[]> commentMap = new LinkedHashMap<>();

        buildCommentMap(type, "", commentMap, new HashSet<>());

        if (commentMap.isEmpty()) {
            return yaml;
        }

        final StringBuilder result = new StringBuilder();
        final List<Frame> frameList = new ArrayList<>();

        for (final String line : yaml.split("\n")) {
            final String trimmed = line.strip();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                result.append(line).append("\n");
                continue;
            }

            int indent = line.length() - line.stripLeading().length();

            while (!(frameList.isEmpty()) && frameList.getLast().indent() >= indent) {
                frameList.removeLast();
            }

            String content = trimmed;

            while (content.equals("-") || content.startsWith("- ")) {
                frameList.add(new Frame(indent, null));

                final String rest = content.substring(1).stripLeading();

                indent += content.length() - rest.length();
                content = rest;
            }

            final String key = getKey(content);

            if (key != null) {
                final String path = getPath(frameList, key);
                final String[] comment = path != null ? commentMap.get(path) : null;

                if (comment != null) {
                    final String commentIndent = line.substring(0, line.length() - line.stripLeading().length());

                    for (final String commentLine : comment) {
                        result.append(commentIndent).append("# ").append(commentLine).append("\n");
                    }
                }

                frameList.add(new Frame(indent, key));
            }

            result.append(line).append("\n");
        }

        return result.toString();
    }

    /**
     * Reads the key from a line of block-style YAML.
     *
     * <p>A quoted key ends at its closing quote, and a plain key ends at the
     * first colon followed by a space or the end of the line.</p>
     *
     * @param content the line with indentation and any list markers removed
     * @return the key, or {@code null} when the line holds none
     */
    private static String getKey(final String content) {
        if (content.isEmpty()) {
            return null;
        }

        final char first = content.charAt(0);

        if (first == '"' || first == '\'') {
            int index = 1;

            while (index < content.length()) {
                final char c = content.charAt(index);

                if (first == '"' && c == '\\') {
                    index += 2;
                    continue;
                }

                if (c == first) {
                    if (first == '\'' && index + 1 < content.length() && content.charAt(index + 1) == '\'') {
                        index += 2;
                        continue;
                    }

                    break;
                }

                index++;
            }

            final int colon = index + 1;

            if (colon >= content.length() || content.charAt(colon) != ':' || (colon + 1 < content.length() && content.charAt(colon + 1) != ' ')) {
                return null;
            }

            return content.substring(1, index);
        }

        final int separator = content.indexOf(": ");

        if (separator > 0) {
            return content.substring(0, separator);
        }

        return content.length() > 1 && content.endsWith(":") ? content.substring(0, content.length() - 1) : null;
    }

    /**
     * Joins the open keys and the given key into a dotted path.
     *
     * @param frameList the open frames, outermost first
     * @param key       the key on the current line
     * @return the dotted path, or {@code null} when the key sits inside a list
     * element and so cannot match a field
     */
    private static String getPath(final List<Frame> frameList, final String key) {
        final StringBuilder path = new StringBuilder();

        for (final Frame frame : frameList) {
            if (frame.key() == null) {
                return null;
            }

            path.append(frame.key()).append(".");
        }

        return path.append(key).toString();
    }

    /**
     * Builds a map of dotted field paths to their {@link Comment @Comment}
     * values by walking the class hierarchy, descending into the fields of
     * nested configuration types.
     *
     * <p>A type already being walked on the current branch is not walked
     * again, so a type that refers to itself cannot recurse forever.</p>
     *
     * @param type          the class to scan
     * @param prefix        the path of the field holding this type, with a
     *                      trailing dot, or empty for the root
     * @param commentMap    the map to fill, in declaration order
     * @param visitingTypes the types being walked on the current branch
     */
    private static void buildCommentMap(final Class<?> type, final String prefix, final Map<String, String[]> commentMap, final Set<Class<?>> visitingTypes) {
        if (!(visitingTypes.add(type))) {
            return;
        }

        Class<?> clazz = type;
        while (clazz != null && clazz != Object.class) {
            for (final Field field : clazz.getDeclaredFields()) {
                final int modifiers = field.getModifiers();

                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                    continue;
                }

                final Comment comment = field.getAnnotation(Comment.class);
                if (comment != null) {
                    commentMap.put(prefix + field.getName(), comment.value());
                }

                if (isNestedType(field.getType())) {
                    buildCommentMap(field.getType(), prefix + field.getName() + ".", commentMap, visitingTypes);
                }
            }
            clazz = clazz.getSuperclass();
        }

        visitingTypes.remove(type);
    }

    /**
     * Whether a field's type is a configuration type of its own, whose fields
     * are written as a nested mapping.
     *
     * <p>Primitives, arrays, enums, interfaces, abstract types and anything
     * from the JDK, including strings, numbers, maps and collections, are
     * written as values rather than walked.</p>
     *
     * @param type the field type
     * @return whether the type is walked for comments
     */
    private static boolean isNestedType(final Class<?> type) {
        return !(type.isPrimitive() || type.isArray() || type.isEnum() || type.isInterface() || Modifier.isAbstract(type.getModifiers()) || type.getName().startsWith("java."));
    }

    /**
     * An open mapping key while comments are injected.
     *
     * @param indent the column the key starts at
     * @param key    the key, or {@code null} for a list item
     */
    private record Frame(
            int indent,
            String key
    ) {
    }
}