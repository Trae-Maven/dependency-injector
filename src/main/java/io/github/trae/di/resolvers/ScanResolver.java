package io.github.trae.di.resolvers;

import io.github.trae.di.annotations.type.Scan;
import io.github.trae.di.containers.ComponentContainer;
import io.github.trae.di.resolvers.abstracts.AbstractResolver;
import io.github.trae.di.resolvers.interfaces.IScanResolver;
import io.github.trae.utilities.UtilJava;

import java.util.*;

/**
 * Resolves the {@link Scan @Scan} base packages for a bootstrap type by
 * walking its full type hierarchy.
 *
 * <p>Starting from the bootstrap type, the resolver performs a breadth-first
 * traversal over both the superclass chain and all implemented interfaces,
 * collecting every {@link Scan @Scan} annotation declared directly on a
 * visited type. This allows each layer of a framework hierarchy — base
 * classes and interfaces alike — to contribute the packages it owns, so the
 * effective scan set is the union of all {@code @Scan} declarations reachable
 * from the bootstrap type.</p>
 *
 * <p>Only annotations physically present on a type are considered, via
 * {@link Class#getDeclaredAnnotation(Class)}. Inheritance is handled
 * explicitly by the traversal rather than relying on annotation inheritance,
 * which would not apply to interfaces and would report a nearest superclass
 * declaration as though it belonged to the bootstrap type.</p>
 *
 * <p>The returned list preserves traversal order — most-derived first — and
 * contains no duplicate package names. A {@code visited} set guards against
 * the repeated visits inherent in interface graphs, where the same type may
 * be reachable through multiple paths (diamonds).</p>
 */
public class ScanResolver extends AbstractResolver implements IScanResolver {

    /**
     * Creates a resolver bound to the given container.
     *
     * @param componentContainer the container this resolver operates against
     */
    public ScanResolver(final ComponentContainer componentContainer) {
        super(componentContainer);
    }

    /**
     * Resolves the ordered, deduplicated list of base packages to scan for the
     * given bootstrap type.
     *
     * <p>The bootstrap type's own package is always included first, so an
     * {@code @Application} class is scanned without needing to declare its own
     * {@link Scan @Scan}. The resolver then performs a breadth-first traversal
     * over the superclass chain and all implemented interfaces, collecting every
     * {@link Scan @Scan} annotation declared directly on a visited type. This
     * lets framework layers higher in the hierarchy contribute the packages they
     * own. {@link Object} and already-visited types are skipped, and duplicate
     * package names are removed while preserving traversal order.</p>
     *
     * @param bootstrapType the type to begin the hierarchy walk from,
     *                      typically the {@code @Application} class
     * @return an ordered, deduplicated list of base packages, beginning with
     * the bootstrap type's own package
     */
    @Override
    public List<String> resolve(final Class<?> bootstrapType) {
        return UtilJava.createCollection(new ArrayList<>(), list -> {
            list.add(bootstrapType.getPackageName());

            final Set<Class<?>> visitedTypeSet = new HashSet<>();
            final Deque<Class<?>> queue = new ArrayDeque<>();

            queue.add(bootstrapType);

            while (!queue.isEmpty()) {
                final Class<?> currentType = queue.poll();

                if (currentType == null || currentType == Object.class || !(visitedTypeSet.add(currentType))) {
                    continue;
                }

                final Scan scan = currentType.getDeclaredAnnotation(Scan.class);
                if (scan != null) {
                    for (final String packageName : this.packageListForClass(currentType, scan)) {
                        if (!(list.contains(packageName))) {
                            list.add(packageName);
                        }
                    }
                }

                queue.add(currentType.getSuperclass());
                queue.addAll(List.of(currentType.getInterfaces()));
            }
        });
    }

    /**
     * Resolves the base packages contributed by a single {@link Scan @Scan}
     * declaration.
     *
     * <p>If the annotation specifies no packages, the declaring type's own
     * package is used; otherwise the explicitly listed packages are used.</p>
     *
     * @param declaringType the type the annotation was declared on
     * @param scan          the {@code @Scan} annotation found on that type
     * @return the base packages this declaration contributes
     */
    private List<String> packageListForClass(final Class<?> declaringType, final Scan scan) {
        if (scan.value().length == 0) {
            return List.of(declaringType.getPackageName());
        }

        return List.of(scan.value());
    }
}