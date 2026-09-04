package io.github.trae.di.resolvers.abstracts;

import io.github.trae.di.containers.ComponentContainer;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * Base class for resolvers that operate against a {@link ComponentContainer}.
 *
 * <p>Holds the container reference and exposes it to subclasses through a
 * protected getter, so each resolver can register and look up instances
 * without managing the container itself.</p>
 */
@Getter(AccessLevel.PROTECTED)
public abstract class AbstractResolver {

    private final ComponentContainer componentContainer;

    /**
     * Creates a resolver bound to the given container.
     *
     * @param componentContainer the container this resolver operates against
     */
    protected AbstractResolver(final ComponentContainer componentContainer) {
        this.componentContainer = componentContainer;
    }
}