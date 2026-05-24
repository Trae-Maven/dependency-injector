package io.github.trae.di.impl;

import io.github.trae.di.InjectorApi;

/**
 * Interface providing toggle functionality for dependency-injected components.
 *
 * <p>Components implementing this interface can be dynamically enabled or disabled
 * at runtime via the {@link InjectorApi} registry.</p>
 */
public interface ToggleableComponent {

    /**
     * Returns whether this component is currently enabled.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     */
    default boolean isComponentEnabled() {
        return InjectorApi.isComponentEnabled(this.getClass());
    }

    /**
     * Sets the enabled state of this component.
     *
     * @param enabled {@code true} to enable, {@code false} to disable
     */
    default void setComponentEnabled(final boolean enabled) {
        InjectorApi.setComponentEnabled(this.getClass(), enabled);
    }
}