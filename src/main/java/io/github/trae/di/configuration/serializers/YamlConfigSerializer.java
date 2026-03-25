package io.github.trae.di.configuration.serializers;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.representer.Representer;

/**
 * {@link ConfigSerializer} implementation using SnakeYAML for YAML format.
 *
 * <p>Produces block-style YAML with 2-space indentation. Field access is
 * set to {@link BeanAccess#FIELD} so that getters/setters are not required
 * — fields are read and written directly, matching the POJO-first approach
 * of the configuration system.</p>
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
        final Yaml yaml = new Yaml(DUMPER_OPTIONS);
        yaml.setBeanAccess(BeanAccess.FIELD);

        return yaml.dump(instance);
    }
}
