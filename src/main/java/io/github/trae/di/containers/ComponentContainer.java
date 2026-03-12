package io.github.trae.di.containers;

import io.github.trae.di.InjectorApi;
import io.github.trae.di.containers.interfaces.IComponentContainer;
import io.github.trae.di.exceptions.ComponentException;
import io.github.trae.utilities.UtilJava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores instantiated components and exposes lookup helpers.
 *
 * <p>Instances are registered under their concrete class. Interface and
 * superclass lookups are handled lazily via
 * {@link #getAssignableInstanceList(Class)}, which caches results after
 * {@link #buildCache()} is called at the end of initialization.</p>
 */
public class ComponentContainer implements IComponentContainer {

    /**
     * Concrete class to singleton instance.
     */
    private final ConcurrentHashMap<Class<?>, Object> instanceMap = new ConcurrentHashMap<>();

    /**
     * Ordered list of discovered component classes.
     */
    private final List<Class<?>> componentClassList = new ArrayList<>();

    /**
     * Lazily populated cache for assignable-type lookups.
     */
    private final ConcurrentHashMap<Class<?>, List<Object>> assignableInstanceCacheMap = new ConcurrentHashMap<>();

    /**
     * Returns an immutable snapshot of all registered instances.
     */
    @Override
    public List<Object> getInstanceList() {
        return List.copyOf(this.instanceMap.values());
    }

    /**
     * Registers a singleton instance under its concrete type.
     *
     * @param type     the concrete class to register under
     * @param instance the singleton instance
     * @throws ComponentException if either argument is null
     */
    @Override
    public void registerInstance(final Class<?> type, final Object instance) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null.");
        }

        if (instance == null) {
            throw new IllegalArgumentException("Instance cannot be null.");
        }

        this.instanceMap.put(type, instance);
    }

    /**
     * Removes the singleton instance registered under the given type.
     *
     * @param type the concrete class to unregister
     * @throws ComponentException if the type is null
     */
    @Override
    public void unregisterInstance(final Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null.");
        }

        this.instanceMap.remove(type);
    }

    /**
     * Returns the instance registered under the exact type.
     *
     * @param type the concrete class to look up
     * @throws ComponentException if the type is null or not registered
     */
    @Override
    public <T> T getInstance(final Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null.");
        }

        final Object instance = this.instanceMap.get(type);
        if (instance == null) {
            throw new ComponentException("No component registered: %s".formatted(type.getName()));
        }

        return UtilJava.cast(type, instance);
    }

    /**
     * Checks whether an instance is registered under the exact type.
     *
     * @param type the concrete class to check
     * @throws ComponentException if the type is null
     */
    @Override
    public boolean isInstance(final Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null.");
        }

        return this.instanceMap.containsKey(type);
    }

    /**
     * Returns all instances assignable to the given type, including
     * subclasses and interface implementations. Results are cached
     * on first access after {@link #buildCache()} has been called.
     *
     * @param type the interface or superclass to match against
     * @throws ComponentException if the type is null
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> List<T> getAssignableInstanceList(final Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null.");
        }

        return (List<T>) this.assignableInstanceCacheMap.computeIfAbsent(type, key ->
                List.copyOf(UtilJava.createCollection(new ArrayList<>(), list -> {
                    for (final Object instance : this.instanceMap.values()) {
                        if (!(key.isAssignableFrom(instance.getClass()))) {
                            continue;
                        }

                        list.add(instance);
                    }
                }))
        );
    }

    /**
     * Returns an unmodifiable view of the registered component classes
     * in their sorted initialization order.
     */
    @Override
    public List<Class<?>> getComponentClassList() {
        return Collections.unmodifiableList(this.componentClassList);
    }

    /**
     * Registers a component class for tracking. Does not create an instance.
     *
     * @param clazz the component class to register
     * @throws ComponentException if the class is null
     */
    @Override
    public void registerComponentClass(final Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Clazz cannot be null.");
        }

        this.componentClassList.add(clazz);
    }

    /**
     * Removes a component class from tracking.
     *
     * @param clazz the component class to unregister
     * @throws ComponentException if the class is null
     */
    @Override
    public void unregisterComponentClass(final Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Clazz cannot be null.");
        }

        this.componentClassList.remove(clazz);
    }

    /**
     * Clears all instances, component classes, and cached lookups.
     * Called during application shutdown.
     */
    @Override
    public void clear() {
        this.instanceMap.clear();
        this.componentClassList.clear();
        this.assignableInstanceCacheMap.clear();
    }

    /**
     * Resets the assignable instance cache so it can be lazily
     * repopulated now that the container is fully initialized.
     * Called once at the end of {@link InjectorApi#initialize(Class)}.
     */
    @Override
    public void buildCache() {
        this.assignableInstanceCacheMap.clear();
    }
}
