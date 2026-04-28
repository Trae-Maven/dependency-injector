package io.github.trae.di.sorters;

import io.github.trae.di.annotations.type.DependsOn;
import io.github.trae.di.annotations.type.Order;
import io.github.trae.di.exceptions.DependencyException;
import io.github.trae.di.sorters.comparators.ComponentComparator;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Sorts component classes to determine initialization order.
 *
 * <p>Sorting is performed in phases:</p>
 * <ol>
 *   <li>Topological sort based on {@link DependsOn} — guarantees that
 *       dependencies are initialized before the components that require them.
 *       Circular references are detected and rejected.</li>
 *   <li>Stable sort by {@link Order} value — lower values are initialized
 *       first. Components without {@code @Order} default to
 *       {@link Integer#MAX_VALUE}.</li>
 *   <li>Additional {@link ComponentComparator} instances — applied in
 *       registration order, allowing external frameworks to refine
 *       initialization order after the core sorting phases.</li>
 * </ol>
 */
public class ComponentSorter {

    private static final List<ComponentComparator> comparatorList = new ArrayList<>();

    /**
     * Registers an additional comparator to be applied after the
     * default sorting phases. Comparators are applied in registration
     * order via {@link Comparator#thenComparing(Comparator)}.
     *
     * @param comparator the comparator to append
     */
    public static void addComparator(final ComponentComparator comparator) {
        if (comparator == null) {
            throw new IllegalArgumentException("Comparator cannot be null.");
        }

        comparatorList.add(comparator);
    }

    /**
     * Removes a previously registered comparator.
     *
     * @param comparator the comparator to remove
     */
    public static void removeComparator(final ComponentComparator comparator) {
        if (comparator == null) {
            throw new IllegalArgumentException("Comparator cannot be null.");
        }

        comparatorList.remove(comparator);
    }

    /**
     * Sorts the given component classes by dependency constraints first,
     * then by priority order, then by any registered external comparators.
     *
     * @param componentClassList the unordered list of component classes
     * @return a new list sorted in initialization order
     * @throws DependencyException if a circular dependency or unregistered
     *                             reference is detected
     */
    public static List<Class<?>> sort(final List<Class<?>> componentClassList) {
        final List<Class<?>> sortedList = topologicalSort(componentClassList);

        Comparator<Class<?>> comparator = Comparator.comparingInt(ComponentSorter::getOrder);

        for (final ComponentComparator additional : comparatorList) {
            comparator = comparator.thenComparing(additional);
        }

        sortedList.sort(comparator);

        return sortedList;
    }

    /**
     * Performs a depth-first topological sort on the dependency graph
     * built from {@link DependsOn} annotations. Dependencies are placed
     * before the components that declare them.
     *
     * <p>Supports interface and superclass references in {@code @DependsOn} —
     * if the declared dependency is an interface or abstract class, it resolves
     * to the first registered component that implements or extends it.</p>
     *
     * @param componentClassList the full list of component classes
     * @return a new list in dependency-safe order
     * @throws DependencyException if a referenced dependency cannot be resolved
     *                             to any registered component or a cycle is found
     */
    private static List<Class<?>> topologicalSort(final List<Class<?>> componentClassList) {
        final Set<Class<?>> componentSet = new HashSet<>(componentClassList);

        final Map<Class<?>, Set<Class<?>>> dependencyMap = new HashMap<>();

        for (final Class<?> type : componentClassList) {
            if (!(type.isAnnotationPresent(DependsOn.class))) {
                continue;
            }

            for (final Class<?> dependency : type.getAnnotation(DependsOn.class).values()) {
                Class<?> resolved = null;

                if (componentSet.contains(dependency)) {
                    resolved = dependency;
                } else {
                    for (final Class<?> candidate : componentSet) {
                        if (dependency.isAssignableFrom(candidate)) {
                            resolved = candidate;
                            break;
                        }
                    }
                }

                if (resolved == null) {
                    if (dependency != null && !(dependency.isInterface()) && !(Modifier.isAbstract(dependency.getModifiers()))) {
                        throw new DependencyException("@%s references unresolvable dependency: %s -> %s".formatted(DependsOn.class.getSimpleName(), type.getName(), dependency.getName()));
                    }
                    continue;
                }

                dependencyMap.computeIfAbsent(type, k -> new HashSet<>()).add(resolved);
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