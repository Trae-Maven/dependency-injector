package io.github.trae.di.configuration.serializers.yaml;

import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@link PropertyUtils} implementation that preserves field declaration
 * order instead of sorting properties alphabetically, so YAML output
 * matches the class layout.
 */
public class OrderedPropertyUtils extends PropertyUtils {

    /**
     * Creates property utils that keep declaration order.
     */
    public OrderedPropertyUtils() {
    }

    /**
     * Builds the property set in declaration order, keeping only readable
     * properties that are writable unless read-only properties are allowed.
     *
     * @param type    the class to introspect
     * @param bAccess the bean access mode
     * @return the ordered property set
     */
    @Override
    protected Set<Property> createPropertySet(final Class<?> type, final BeanAccess bAccess) {
        final Set<Property> propertySet = new LinkedHashSet<>();

        final Collection<Property> propertyCollection = getPropertiesMap(type, bAccess).values();

        for (final Property property : propertyCollection) {
            if (property.isReadable() && (isAllowReadOnlyProperties() || property.isWritable())) {
                propertySet.add(property);
            }
        }

        return propertySet;
    }
}