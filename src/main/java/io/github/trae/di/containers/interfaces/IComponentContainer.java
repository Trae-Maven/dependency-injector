package io.github.trae.di.containers.interfaces;

import java.util.List;

/**
 * Stores component classes and their singleton instances, and exposes
 * exact-type and assignable-type lookups over them.
 */
public interface IComponentContainer {

    /**
     * Returns an immutable snapshot of all registered instances.
     *
     * @return every registered singleton instance
     */
    List<Object> getInstanceList();

    /**
     * Registers a singleton instance under its concrete type.
     *
     * @param type     the concrete class to register under
     * @param instance the singleton instance
     */
    void registerInstance(final Class<?> type, final Object instance);

    /**
     * Removes the singleton instance registered under the given type.
     *
     * @param type the concrete class to unregister
     */
    void unregisterInstance(final Class<?> type);

    /**
     * Returns the instance registered under the exact type.
     *
     * @param type the concrete class to look up
     * @param <T>  the component type
     * @return the registered instance
     */
    <T> T getInstance(final Class<T> type);

    /**
     * Checks whether an instance is registered under the exact type.
     *
     * @param type the concrete class to check
     * @return {@code true} if an instance is registered
     */
    boolean isInstance(final Class<?> type);

    /**
     * Returns all instances assignable to the given type, including
     * subclasses and interface implementations.
     *
     * @param type the interface or superclass to match against
     * @param <T>  the component type
     * @return every matching instance
     */
    <T> List<T> getAssignableInstanceList(final Class<T> type);

    /**
     * Returns the registered component classes in their sorted
     * initialization order.
     *
     * @return the tracked component classes
     */
    List<Class<?>> getComponentClassList();

    /**
     * Registers a component class for tracking without creating an instance.
     *
     * @param clazz the component class to register
     */
    void registerComponentClass(final Class<?> clazz);

    /**
     * Removes a component class from tracking.
     *
     * @param clazz the component class to unregister
     */
    void unregisterComponentClass(final Class<?> clazz);

    /**
     * Clears all instances, component classes, and cached lookups.
     */
    void clear();

    /**
     * Resets the assignable-type lookup cache so it can be repopulated
     * against the container's current contents.
     */
    void buildCache();
}