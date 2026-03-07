package io.github.trae.di.resolvers;

import io.github.trae.di.annotations.Inject;
import io.github.trae.di.containers.ComponentContainer;
import io.github.trae.di.exceptions.DependencyException;
import io.github.trae.di.resolvers.abstracts.AbstractResolver;
import io.github.trae.di.resolvers.interfaces.IFieldResolver;
import io.github.trae.utilities.UtilField;

import java.lang.reflect.Field;
import java.util.Collection;

/**
 * Injects dependencies into {@link Inject @Inject}-annotated fields
 * of an already-constructed component instance.
 *
 * <p>All fields in the instance's class hierarchy (up to but excluding
 * {@link Object}) are scanned. Each annotated field is resolved via
 * {@link DependencyResolver} and assigned reflectively, supporting
 * both single-type and collection-type dependencies.</p>
 */
public class FieldResolver extends AbstractResolver implements IFieldResolver {

    private final DependencyResolver dependencyResolver;

    public FieldResolver(final ComponentContainer componentContainer) {
        super(componentContainer);

        this.dependencyResolver = new DependencyResolver(componentContainer);
    }

    /**
     * Scans the instance's class hierarchy and injects all
     * {@link Inject @Inject}-annotated fields from the container.
     *
     * @param instance the component instance to inject into
     * @throws DependencyException if a dependency cannot be resolved
     *                             or field assignment fails
     */
    @Override
    public void inject(final Object instance) {
        Class<?> clazz = instance.getClass();

        while (clazz != null && clazz != Object.class) {
            for (final Field field : clazz.getDeclaredFields()) {
                if (!(field.isAnnotationPresent(Inject.class))) {
                    continue;
                }

                try {
                    final Object dependency;

                    if (Collection.class.isAssignableFrom(field.getType())) {
                        dependency = this.dependencyResolver.resolveCollection(field.getGenericType(), field.getType());
                    } else {
                        dependency = this.dependencyResolver.resolve(field.getType());
                    }

                    UtilField.set(field, instance, dependency);
                } catch (final DependencyException e) {
                    throw e;
                } catch (final Exception e) {
                    throw new DependencyException("Failed to inject field: %s.%s".formatted(clazz.getName(), field.getName()), e);
                }
            }

            clazz = clazz.getSuperclass();
        }
    }
}