package io.github.trae.di.resolvers;

import io.github.trae.di.annotations.type.Scan;
import io.github.trae.di.containers.ComponentContainer;
import io.github.trae.di.resolvers.abstracts.AbstractResolver;
import io.github.trae.di.resolvers.interfaces.IScanResolver;
import io.github.trae.utilities.UtilJava;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the {@link Scan @Scan} system base packages for a bootstrap type
 * by walking its full type hierarchy.
 *
 * <p>Starting from the bootstrap type, the resolver performs a breadth-first
 * traversal over both the superclass chain and all implemented interfaces,
 * collecting every {@link Scan @Scan} annotation declared directly on a
 * visited type. Every package contributed by a {@code @Scan} is treated as a
 * <em>system</em> package — its components are framework-level and shared
 * across all applications, owned by the container rather than any single
 * application. The bootstrap type's own package is <strong>not</strong>
 * included here; it is application-scoped and handled separately by the
 * caller.</p>
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
     * Resolves the ordered, deduplicated list of system base packages declared
     * via {@link Scan @Scan} anywhere in the bootstrap type's superclass and
     * interface hierarchy.
     *
     * <p>Each package returned originates from a {@code @Scan} declaration and
     * is therefore system-scoped — its components belong to a framework or
     * library shared across applications, not to the booting application
     * itself. The bootstrap type's own package is intentionally excluded; the
     * caller scans that separately as application-scoped.</p>
     *
     * <p>The traversal is breadth-first: at each type the superclass is
     * enqueued before its interfaces, so packages from more-derived types
     * appear earlier in the result. {@link Object}, already-visited types, and
     * types with no superclass are handled safely.</p>
     *
     * @param bootstrapType the type to begin the hierarchy walk from,
     *                      typically the {@code @Application} class
     * @return an ordered, deduplicated list of system base packages, or an
     * empty list if no {@code @Scan} is present in the hierarchy
     */
    @Override
    public List<String> resolve(final Class<?> bootstrapType) {
        return UtilJava.createCollection(new ArrayList<>(), list -> {
            final Set<Class<?>> visitedTypeSet = new HashSet<>();
            final Deque<Class<?>> queue = new ArrayDeque<>();

            queue.add(bootstrapType);

            while (!queue.isEmpty()) {
                final Class<?> currentType = queue.poll();

                if (currentType == Object.class || !(visitedTypeSet.add(currentType))) {
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

                final Class<?> superClassType = currentType.getSuperclass();
                if (superClassType != null) {
                    queue.add(superClassType);
                }

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