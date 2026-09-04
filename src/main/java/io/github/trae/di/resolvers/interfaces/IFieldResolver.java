package io.github.trae.di.resolvers.interfaces;

/**
 * Injects container dependencies into the annotated fields of an
 * already-constructed component instance.
 */
public interface IFieldResolver {

    /**
     * Scans the instance's class hierarchy and injects every annotated field.
     *
     * @param instance the component instance to inject into
     */
    void inject(final Object instance);
}