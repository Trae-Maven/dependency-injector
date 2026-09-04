package io.github.trae.di.resolvers.interfaces;

/**
 * Instantiates components by resolving their constructor dependencies
 * from the container.
 */
public interface IConstructorResolver {

    /**
     * Creates a singleton instance of the given type and registers it
     * into the container.
     *
     * @param type the component class to instantiate
     * @return the created instance
     */
    Object create(final Class<?> type);
}