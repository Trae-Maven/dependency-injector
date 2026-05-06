package io.github.trae.di.resolvers;

import io.github.trae.di.containers.ComponentContainer;
import io.github.trae.di.exceptions.DependencyException;
import io.github.trae.di.resolvers.abstracts.AbstractResolver;
import io.github.trae.di.resolvers.interfaces.IDependencyResolver;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves dependencies from the container by type.
 *
 * <p>Single dependencies are resolved by exact type first, then by
 * assignable-type scan. If the dependency is a registered component
 * class that has not yet been instantiated, it is constructed on demand
 * via the {@link ConstructorResolver}. Collection dependencies are
 * resolved by extracting the generic element type and gathering all
 * assignable instances into a {@link List} or {@link Set}.</p>
 *
 * <p>Assignable-type resolution checks both already-built instances and
 * unbuilt component classes directly against the instance map, bypassing
 * the assignable cache to avoid stale empty results during initialization.</p>
 */
public class DependencyResolver extends AbstractResolver implements IDependencyResolver {

    private ConstructorResolver constructorResolver;

    public DependencyResolver(final ComponentContainer componentContainer) {
        super(componentContainer);
    }

    /**
     * Sets the {@link ConstructorResolver} used for lazy construction
     * of dependencies that have not yet been instantiated.
     *
     * @param constructorResolver the constructor resolver to delegate to
     */
    void setConstructorResolver(final ConstructorResolver constructorResolver) {
        this.constructorResolver = constructorResolver;
    }

    /**
     * Resolves a single dependency by type. Checks for an exact match
     * first, then scans the component class list for assignable types,
     * handling both already-built and unbuilt components directly to
     * avoid stale assignable cache results during initialization.
     *
     * @param type the dependency type to resolve
     * @return the resolved instance
     * @throws DependencyException if no matching component is found
     */
    @Override
    public Object resolve(final Class<?> type) {
        if (this.getComponentContainer().isInstance(type)) {
            return this.getComponentContainer().getInstance(type);
        }

        // Attempt lazy construction if the type is a registered but unbuilt component
        if (this.constructorResolver != null) {
            for (final Class<?> componentClass : this.getComponentContainer().getComponentClassList()) {
                if (componentClass == type && !(this.getComponentContainer().isInstance(componentClass))) {
                    this.constructorResolver.create(componentClass);
                    return this.getComponentContainer().getInstance(componentClass);
                }
            }
        }

        // Attempt resolution by assignable type — check both built and unbuilt components
        // directly against the component class list to avoid stale assignable cache entries
        if (this.constructorResolver != null) {
            for (final Class<?> componentClass : this.getComponentContainer().getComponentClassList()) {
                if (type.isAssignableFrom(componentClass)) {
                    if (!(this.getComponentContainer().isInstance(componentClass))) {
                        this.constructorResolver.create(componentClass);
                    }
                    return this.getComponentContainer().getInstance(componentClass);
                }
            }
        }

        throw new DependencyException("No component found for dependency: %s".formatted(type.getName()));
    }

    /**
     * Resolves a collection dependency by extracting the generic element
     * type and gathering all assignable instances into the appropriate
     * collection type.
     *
     * <p>Supports both simple generic types (e.g. {@code List<Progression>})
     * and parameterized generic types (e.g. {@code List<Progression<BreakBlockEvent>>}).
     * For parameterized element types, the raw type is extracted and used
     * for resolution, as generic type arguments are erased at runtime.</p>
     *
     * @param genericType the parameterized field or parameter type
     * @param rawType     the raw collection class ({@link List} or {@link Set})
     * @return a new collection containing all matching instances
     * @throws DependencyException if the generic type cannot be resolved
     *                             or the collection type is unsupported
     */
    @Override
    public Object resolveCollection(final Type genericType, final Class<?> rawType) {
        if (!(genericType instanceof final ParameterizedType parameterizedType)) {
            throw new DependencyException("Collection injection requires generic type.");
        }

        final Type actualTypeArgument = parameterizedType.getActualTypeArguments()[0];

        final Class<?> elementClass;

        if (actualTypeArgument instanceof final Class<?> clazz) {
            elementClass = clazz;
        } else if (actualTypeArgument instanceof final ParameterizedType parameterizedElementType) {
            if (!(parameterizedElementType.getRawType() instanceof final Class<?> rawElementClass)) {
                throw new DependencyException("Unsupported generic dependency type: %s".formatted(actualTypeArgument.getTypeName()));
            }
            elementClass = rawElementClass;
        } else {
            throw new DependencyException("Unsupported generic dependency type: %s".formatted(actualTypeArgument.getTypeName()));
        }

        final List<?> assignableInstanceList = this.getComponentContainer().getAssignableInstanceList(elementClass);

        if (List.class.isAssignableFrom(rawType)) {
            return new ArrayList<>(assignableInstanceList);
        }

        if (Set.class.isAssignableFrom(rawType)) {
            return new HashSet<>(assignableInstanceList);
        }

        throw new DependencyException("Unsupported collection type: %s".formatted(rawType.getName()));
    }
}