package io.github.trae.di.containers.interfaces;

import java.util.List;

public interface IComponentContainer {

    List<Object> getInstanceList();

    void registerInstance(final Class<?> type, final Object instance);

    void unregisterInstance(final Class<?> type);

    <T> T getInstance(final Class<T> type);

    boolean isInstance(final Class<?> type);

    <T> List<T> getAssignableInstanceList(final Class<T> type);

    List<Class<?>> getComponentClassList();

    void registerComponentClass(final Class<?> clazz);

    void unregisterComponentClass(final Class<?> clazz);

    void clear();

    void buildCache();
}