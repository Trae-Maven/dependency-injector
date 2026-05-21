package io.github.trae.di.impl;

import io.github.trae.di.InjectorApi;

public interface ToggleableComponent {

    default boolean isComponentEnabled() {
        return InjectorApi.isComponentEnabled(this.getClass());
    }

    default void setComponentEnabled(final boolean enabled) {
        InjectorApi.setComponentEnabled(this.getClass(), enabled);
    }
}