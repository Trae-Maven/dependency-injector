package io.github.trae.di.configuration.serializers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.trae.di.configuration.annotations.Comment;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ConfigSerializer} implementation using Gson for JSON format.
 *
 * <p>Produces pretty-printed JSON with HTML escaping disabled. Fields
 * annotated with {@link Comment @Comment} have their comments injected
 * as {@code //} lines above the corresponding key in the output, including
 * fields of nested configuration types.</p>
 *
 * <p>Note: the resulting output is not strictly valid JSON due to the
 * injected comments, but is human-readable and parsed correctly on
 * deserialization since comments are stripped before parsing.</p>
 */
public class JsonConfigSerializer implements ConfigSerializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * Creates a serializer backed by the shared Gson instance.
     */
    public JsonConfigSerializer() {
    }

    @Override
    public <T> T deserialize(final String content, final Class<T> type) {
        return GSON.fromJson(stripComments(content), type);
    }

    @Override
    public String serialize(final Object instance) {
        final String raw = GSON.toJson(instance);

        return injectComments(raw, instance.getClass());
    }

    /**
     * Strips {@code //} single-line comments from the given JSON string,
     * respecting string literals so that {@code //} inside quoted values
     * is preserved.
     *
     * @param json the JSON string potentially containing comments
     * @return the JSON string with comments removed
     */
    private static String stripComments(final String json) {
        final StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            final char c = json.charAt(i);

            if (escaped) {
                result.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                result.append(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                result.append(c);
                continue;
            }

            if (!inString && c == '/' && i + 1 < json.length()) {
                if (json.charAt(i + 1) == '/') {
                    // Skip until end of line
                    while (i < json.length() && json.charAt(i) != '\n') {
                        i++;
                    }
                    continue;
                }
            }

            result.append(c);
        }

        return result.toString();
    }

    /**
     * Injects {@link Comment @Comment} annotations as {@code //} comment
     * lines above their corresponding JSON keys.
     *
     * <p>Keys are matched by their full path from the root, such as
     * {@code delay.defaultValue}, rather than by name alone, so a nested
     * field never picks up the comment of a same-named field elsewhere.
     * The path is tracked with a stack pushed for every line that opens an
     * object or array and popped for every line that closes one. Anything
     * opened without a key, the root and array elements, pushes a
     * {@code null} marker, so keys inside array elements never match.</p>
     *
     * @param json the serialized JSON string
     * @param type the configuration class to read annotations from
     * @return the JSON string with comments injected
     */
    private static String injectComments(final String json, final Class<?> type) {
        final Map<String, String[]> commentMap = new LinkedHashMap<>();

        buildCommentMap(type, "", commentMap, new HashSet<>());

        if (commentMap.isEmpty()) {
            return json;
        }

        final StringBuilder result = new StringBuilder();
        final List<String> pathList = new ArrayList<>();

        for (final String line : json.split("\n")) {
            final String trimmed = line.trim();

            if (trimmed.startsWith("}") || trimmed.startsWith("]")) {
                if (!(pathList.isEmpty())) {
                    pathList.removeLast();
                }

                result.append(line).append("\n");
                continue;
            }

            final String key = getKey(trimmed);

            if (key != null) {
                final String path = getPath(pathList, key);
                final String[] comment = path != null ? commentMap.get(path) : null;

                if (comment != null) {
                    final String indent = line.substring(0, line.length() - line.stripLeading().length());

                    for (final String commentLine : comment) {
                        result.append(indent).append("// ").append(commentLine).append("\n");
                    }
                }
            }

            result.append(line).append("\n");

            if (trimmed.endsWith("{") || trimmed.endsWith("[")) {
                pathList.add(key);
            }
        }

        return result.toString().stripTrailing() + "\n";
    }

    /**
     * Reads the key from a line of pretty-printed JSON.
     *
     * <p>Gson writes every key as a quoted string immediately followed by a
     * colon, which is what separates a key from a string element of an
     * array.</p>
     *
     * @param trimmed the line with surrounding whitespace removed
     * @return the key, or {@code null} when the line holds none
     */
    private static String getKey(final String trimmed) {
        if (!(trimmed.startsWith("\""))) {
            return null;
        }

        int index = 1;

        while (index < trimmed.length()) {
            final char c = trimmed.charAt(index);

            if (c == '\\') {
                index += 2;
                continue;
            }

            if (c == '"') {
                break;
            }

            index++;
        }

        if (index + 1 >= trimmed.length() || trimmed.charAt(index + 1) != ':') {
            return null;
        }

        return trimmed.substring(1, index);
    }

    /**
     * Joins the open keys and the given key into a dotted path, skipping the
     * root.
     *
     * @param pathList the keys of every open object or array, root first
     * @param key      the key on the current line
     * @return the dotted path, or {@code null} when the key sits inside an
     * array element and so cannot match a field
     */
    private static String getPath(final List<String> pathList, final String key) {
        final StringBuilder path = new StringBuilder();

        for (int i = 1; i < pathList.size(); i++) {
            final String part = pathList.get(i);

            if (part == null) {
                return null;
            }

            path.append(part).append(".");
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
     * are written as a nested object.
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
}