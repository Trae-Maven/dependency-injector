package io.github.trae.di.sorters;

import io.github.trae.di.annotations.DependsOn;
import io.github.trae.di.annotations.Order;
import io.github.trae.di.exceptions.DependencyException;

import java.util.*;

/**
 * Sorts component classes to determine initialization order.
 *
 * <p>Sorting is performed in two phases:</p>
 * <ol>
 *   <li>Topological sort based on {@link DependsOn} — guarantees that
 *       dependencies are initialized before the components that require them.
 *       Circular references are detected and rejected.</li>
 *   <li>Stable sort by {@link Order} value — lower values are initialized
 *       first. Components without {@code @Order} default to
 *       {@link Integer#MAX_VALUE}.</li>
 * </ol>
 */
public class ComponentSorter {

    /**
     * Sorts the given component classes by dependency constraints first,
     * then by priority order.
     *
     * @param componentClassList the unordered list of component classes
     * @return a new list sorted in initialization order
     * @throws DependencyException if a circular dependency or unregistered
     *                             reference is detected
     */
    public static List<Class<?>> sort(final List<Class<?>> componentClassList) {
        final List<Class<?>> dependenciesSortedList = topologicalSort(componentClassList);

        dependenciesSortedList.sort(Comparator.comparingInt(ComponentSorter::getOrder));

        return dependenciesSortedList;
    }

    /**
     * Performs a depth-first topological sort on the dependency graph
     * built from {@link DependsOn} annotations. Dependencies are placed
     * before the components that declare them.
     *
     * @param componentClassList the full list of component classes
     * @return a new list in dependency-safe order
     * @throws DependencyException if a referenced dependency is not a
     *                             registered component or a cycle is found
     */
    private static List<Class<?>> topologicalSort(final List<Class<?>> componentClassList) {
        final Set<Class<?>> componentSet = new HashSet<>(componentClassList);

        final Map<Class<?>, Set<Class<?>>> dependencyMap = new HashMap<>();

        for (final Class<?> type : componentClassList) {
            if (!(type.isAnnotationPresent(DependsOn.class))) {
                continue;
            }

            for (final Class<?> dependency : type.getAnnotation(DependsOn.class).values()) {
                if (!(componentSet.contains(dependency))) {
                    throw new DependencyException("@%s references unregistered component: %s -> %s".formatted(DependsOn.class.getSimpleName(), type.getName(), dependency.getName()));
                }

                dependencyMap.computeIfAbsent(type, k -> new HashSet<>()).add(dependency);
            }
        }

        final LinkedHashSet<Class<?>> sorted = new LinkedHashSet<>();
        final Set<Class<?>> visiting = new HashSet<>();

        for (final Class<?> type : componentClassList) {
            visit(type, dependencyMap, sorted, visiting);
        }

        return new ArrayList<>(sorted);
    }

    /**
     * Recursively visits a node in the dependency graph. Tracks nodes
     * currently on the call stack via {@code visiting} to detect cycles.
     *
     * @param type          the component class being visited
     * @param dependencyMap the full dependency graph
     * @param sorted        the accumulated result in insertion order
     * @param visiting      nodes currently on the recursion stack
     * @throws DependencyException if a cycle is detected
     */
    private static void visit(final Class<?> type, final Map<Class<?>, Set<Class<?>>> dependencyMap, final LinkedHashSet<Class<?>> sorted, final Set<Class<?>> visiting) {
        if (sorted.contains(type)) {
            return;
        }

        if (visiting.contains(type)) {
            throw new DependencyException("Circular @%s detected: %s".formatted(DependsOn.class.getSimpleName(), type.getName()));
        }

        visiting.add(type);

        for (final Class<?> dependencyClass : dependencyMap.getOrDefault(type, Collections.emptySet())) {
            visit(dependencyClass, dependencyMap, sorted, visiting);
        }

        visiting.remove(type);
        sorted.add(type);
    }

    /**
     * Returns the {@link Order} value for a component class, or
     * {@link Integer#MAX_VALUE} if the annotation is not present.
     */
    private static int getOrder(final Class<?> type) {
        return type.isAnnotationPresent(Order.class) ? type.getAnnotation(Order.class).value() : Integer.MAX_VALUE;
    }
}