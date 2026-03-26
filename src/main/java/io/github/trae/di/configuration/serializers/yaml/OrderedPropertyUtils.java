package io.github.trae.di.configuration.serializers.yaml;

import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class OrderedPropertyUtils extends PropertyUtils {

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