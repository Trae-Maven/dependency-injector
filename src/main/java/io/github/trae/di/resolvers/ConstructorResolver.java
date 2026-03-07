package io.github.trae.di.resolvers;

import io.github.trae.di.containers.ComponentContainer;
import io.github.trae.di.exceptions.DependencyException;
import io.github.trae.di.resolvers.abstracts.AbstractResolver;
import io.github.trae.di.resolvers.interfaces.IConstructorResolver;
import io.github.trae.utilities.UtilClass;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Creates component instances by resolving and injecting constructor
 * dependencies from the container.
 *
 * <p>The constructor with the most parameters is selected automatically.
 * Each parameter is resolved via {@link DependencyResolver}, supporting
 * both single-type and collection-type dependencies. Circular dependencies
 * are detected at creation time and result in a {@link DependencyException}.</p>
 *
 * <p>Successfully created instances are immediately registered in the
 * container, making them available for subsequent resolutions.</p>
 */
public class ConstructorResolver extends AbstractResolver implements IConstructorResolver {

    private final DependencyResolver dependencyResolver;

    /**
     * Tracks types currently being resolved to detect circular dependencies.
     */
    private final Set<Class<?>> resolvingSet = new HashSet<>();

    public ConstructorResolver(final ComponentContainer componentContainer) {
        super(componentContainer);

        this.dependencyResolver = new DependencyResolver(componentContainer);
    }

    /**
     * Creates a singleton instance of the given type by resolving its
     * constructor dependencies and registering the result in the container.
     *
     * @param type the component class to instantiate
     * @return the created instance
     * @throws DependencyException if a circular dependency is detected,
     *                             a dependency cannot be resolved, or
     *                             instantiation fails
     */
    @Override
    public Object create(final Class<?> type) {
        if (!(this.resolvingSet.add(type))) {
            throw new DependencyException("Circular dependency detected: %s".formatted(type.getName()));
        }

        try {
            final Constructor<?> constructor = this.selectConstructor(type);

            final Parameter[] parameters = constructor.getParameters();

            final Object[] args = new Object[parameters.length];

            for (int index = 0; index < parameters.length; index++) {
                final Parameter parameter = parameters[index];

                final Class<?> parameterType = parameter.getType();

                if (Collection.class.isAssignableFrom(parameterType)) {
                    args[index] = this.dependencyResolver.resolveCollection(parameter.getParameterizedType(), parameterType);
                } else {
                    args[index] = this.dependencyResolver.resolve(parameterType);
                }
            }

            final Object instance = UtilClass.create(type, args);

            this.getComponentContainer().registerInstance(type, instance);

            return instance;
        } catch (final DependencyException e) {
            throw e;
        } catch (final Exception e) {
            throw new DependencyException("Failed to create component: %s".formatted(type.getName()), e);
        } finally {
            this.resolvingSet.remove(type);
        }
    }

    /**
     * Selects the constructor with the most parameters, allowing the
     * container to satisfy the widest set of dependencies automatically.
     *
     * @param type the class to inspect
     * @return the greediest declared constructor
     */
    private Constructor<?> selectConstructor(final Class<?> type) {
        final Constructor<?>[] declaredConstructors = type.getDeclaredConstructors();

        Constructor<?> selectedDeclaredConstructor = declaredConstructors[0];

        for (final Constructor<?> declaredConstructor : declaredConstructors) {
            if (declaredConstructor.getParameterCount() > selectedDeclaredConstructor.getParameterCount()) {
                selectedDeclaredConstructor = declaredConstructor;
            }
        }

        return selectedDeclaredConstructor;
    }
}