package io.github.trae.di.resolvers.interfaces;

import java.util.List;

/**
 * Resolves the system base packages declared across a bootstrap type's
 * superclass and interface hierarchy.
 */
public interface IScanResolver {

    /**
     * Resolves the ordered, deduplicated list of system base packages for
     * the given bootstrap type.
     *
     * @param bootstrapType the type to begin the hierarchy walk from
     * @return the resolved base packages, or an empty list if none are declared
     */
    List<String> resolve(final Class<?> bootstrapType);
}