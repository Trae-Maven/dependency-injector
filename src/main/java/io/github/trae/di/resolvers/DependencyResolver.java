package io.github.trae.di.resolvers;

import io.github.trae.di.containers.ComponentContainer;
import io.github.trae.di.exceptions.DependencyException;
import io.github.trae.di.resolvers.abstracts.AbstractResolver;
import io.github.trae.di.resolvers.interfaces.IDependencyResolver;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves dependencies from the container by type.
 *
 * <p>Single dependencies are resolved by exact type first, then by
 * assignable-type scan. Collection dependencies are resolved by
 * extracting the generic element type and gathering all assignable
 * instances into a {@link List} or {@link Set}.</p>
 */
public class DependencyResolver extends AbstractResolver implements IDependencyResolver {

    public DependencyResolver(final ComponentContainer componentContainer) {
        super(componentContainer);
    }

    /**
     * Resolves a single dependency by type. Checks for an exact match
     * first, then falls back to an assignable-type lookup.
     *
     * @param type the dependency type to resolve
     * @return the resolved instance
     * @throws DependencyException if no matching component is found
     */
    @Override
    public Object resolve(final Class<?> type) {
        if (this.getComponentContainer().isInstance(type)) {
            return this.getComponentContainer().getInstance(type);
        }

        final List<?> assignableInstanceList = this.getComponentContainer().getAssignableInstanceList(type);

        if (assignableInstanceList.isEmpty()) {
            throw new DependencyException("No component found for dependency: %s".formatted(type.getName()));
        }

        return assignableInstanceList.getFirst();
    }

    /**
     * Resolves a collection dependency by extracting the generic element
     * type and gathering all assignable instances into the appropriate
     * collection type.
     *
     * @param genericType the parameterized field or parameter type
     * @param rawType     the raw collection class ({@link List} or {@link Set})
     * @return a new collection containing all matching instances
     * @throws DependencyException if the generic type cannot be resolved
     *                             or the collection type is unsupported
     */
    @Override
    public Object resolveCollection(final Type genericType, final Class<?> rawType) {
        if (!(genericType instanceof final ParameterizedType parameterizedType)) {
            throw new DependencyException("Collection injection requires generic type.");
        }

        final Type actualTypeArgument = parameterizedType.getActualTypeArguments()[0];

        if (!(actualTypeArgument instanceof final Class<?> elementClass)) {
            throw new DependencyException("Unsupported generic dependency type.");
        }

        final List<?> assignableInstanceList = this.getComponentContainer().getAssignableInstanceList(elementClass);

        if (List.class.isAssignableFrom(rawType)) {
            return new ArrayList<>(assignableInstanceList);
        }

        if (Set.class.isAssignableFrom(rawType)) {
            return new HashSet<>(assignableInstanceList);
        }

        throw new DependencyException("Unsupported collection type: %s".formatted(rawType.getName()));
    }
}