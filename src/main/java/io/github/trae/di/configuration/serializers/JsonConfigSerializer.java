package io.github.trae.di.configuration.serializers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.trae.di.configuration.annotations.Comment;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link ConfigSerializer} implementation using Gson for JSON format.
 *
 * <p>Produces pretty-printed JSON with HTML escaping disabled. Fields
 * annotated with {@link Comment @Comment} have their comments injected
 * as {@code //} lines above the corresponding key in the output.</p>
 *
 * <p>Note: the resulting output is not strictly valid JSON due to the
 * injected comments, but is human-readable and parsed correctly on
 * deserialization since comments are stripped before parsing.</p>
 */
public class JsonConfigSerializer implements ConfigSerializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

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
     * @param json the serialized JSON string
     * @param type the configuration class to read annotations from
     * @return the JSON string with comments injected
     */
    private static String injectComments(final String json, final Class<?> type) {
        final Map<String, String[]> commentMap = buildCommentMap(type);

        if (commentMap.isEmpty()) {
            return json;
        }

        final StringBuilder result = new StringBuilder();

        for (final String line : json.split("\n")) {
            final String trimmed = line.trim();

            for (final Map.Entry<String, String[]> entry : commentMap.entrySet()) {
                if (trimmed.startsWith("\"" + entry.getKey() + "\"")) {
                    final String indent = line.substring(0, line.indexOf(trimmed));
                    for (final String commentLine : entry.getValue()) {
                        result.append(indent).append("// ").append(commentLine).append("\n");
                    }
                    break;
                }
            }

            result.append(line).append("\n");
        }

        return result.toString().stripTrailing() + "\n";
    }

    /**
     * Builds a map of field names to their {@link Comment @Comment} values
     * by walking the class hierarchy.
     *
     * @param type the class to scan
     * @return ordered map of field name to comment lines
     */
    private static Map<String, String[]> buildCommentMap(final Class<?> type) {
        final Map<String, String[]> commentMap = new LinkedHashMap<>();

        Class<?> clazz = type;
        while (clazz != null && clazz != Object.class) {
            for (final Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                final Comment comment = field.getAnnotation(Comment.class);
                if (comment != null) {
                    commentMap.put(field.getName(), comment.value());
                }
            }
            clazz = clazz.getSuperclass();
        }

        return commentMap;
    }
}