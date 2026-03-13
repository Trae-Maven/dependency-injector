package io.github.trae.di.sorters.comparators;

import io.github.trae.di.sorters.ComponentSorter;

import java.util.Comparator;

/**
 * A comparator applied to component classes after topological sorting
 * and {@link io.github.trae.di.annotations.type.Order @Order} priority
 * resolution.
 *
 * <p>Additional comparators are chained in registration order via
 * {@link ComponentSorter#addComparator(ComponentComparator)}, allowing
 * external frameworks to influence initialization order without
 * modifying the core sorting logic.</p>
 */
@FunctionalInterface
public interface ComponentComparator extends Comparator<Class<?>> {
}