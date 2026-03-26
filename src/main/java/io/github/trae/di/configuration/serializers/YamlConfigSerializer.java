package io.github.trae.di.configuration.serializers;

import io.github.trae.di.configuration.annotations.Comment;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link ConfigSerializer} implementation using SnakeYAML for YAML format.
 *
 * <p>Produces block-style YAML with 2-space indentation. Field access is
 * set to {@link BeanAccess#FIELD} so that getters/setters are not required
 * — fields are read and written directly, matching the POJO-first approach
 * of the configuration system.</p>
 *
 * <p>Fields annotated with {@link Comment @Comment} have their comments
 * injected as {@code #} lines above the corresponding key in the output.</p>
 */
public class YamlConfigSerializer implements ConfigSerializer {

    private static final DumperOptions DUMPER_OPTIONS = createDumperOptions();

    private static DumperOptions createDumperOptions() {
        final DumperOptions options = new DumperOptions();

        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);

        return options;
    }

    @Override
    public <T> T deserialize(final String content, final Class<T> type) {
        final Representer representer = new Representer(DUMPER_OPTIONS);
        representer.getPropertyUtils().setSkipMissingProperties(true);

        final Yaml yaml = new Yaml(new Constructor(type, new LoaderOptions()), representer, DUMPER_OPTIONS);
        yaml.setBeanAccess(BeanAccess.FIELD);

        return yaml.loadAs(content, type);
    }

    @Override
    public String serialize(final Object instance) {
        final Representer representer = new Representer(DUMPER_OPTIONS);
        representer.addClassTag(instance.getClass(), Tag.MAP);

        final Yaml yaml = new Yaml(representer, DUMPER_OPTIONS);

        yaml.setBeanAccess(BeanAccess.FIELD);

        return injectComments(yaml.dump(instance), instance.getClass());
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
            for (final Map.Entry<String, String[]> entry : commentMap.entrySet()) {
                if (line.startsWith(entry.getKey() + ":")) {
                    for (final String commentLine : entry.getValue()) {
                        result.append("# ").append(commentLine).append("\n");
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