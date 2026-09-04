package io.github.trae.di.resolvers.interfaces;

import java.lang.reflect.Type;

/**
 * Resolves dependencies from the container by type, for both single
 * values and collections.
 */
public interface IDependencyResolver {

    /**
     * Resolves a single dependency by exact type, falling back to an
     * assignable-type match.
     *
     * @param type the dependency type to resolve
     * @return the resolved instance
     */
    Object resolve(final Class<?> type);

    /**
     * Resolves a collection dependency by gathering every instance
     * assignable to the collection's generic element type.
     *
     * @param genericType the parameterized field or parameter type
     * @param rawType     the raw collection class
     * @return a new collection containing all matching instances
     */
    Object resolveCollection(final Type genericType, final Class<?> rawType);
}