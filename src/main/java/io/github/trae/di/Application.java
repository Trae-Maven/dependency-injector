package io.github.trae.di;

import io.github.trae.di.annotations.*;
import io.github.trae.di.containers.ComponentContainer;
import io.github.trae.di.exceptions.InjectorException;
import io.github.trae.di.resolvers.ConstructorResolver;
import io.github.trae.di.resolvers.FieldResolver;
import io.github.trae.di.sorters.ComponentSorter;
import io.github.trae.utilities.UtilJava;
import lombok.Getter;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Entry point for the dependency injection framework.
 *
 * <p>Manages the full application lifecycle:</p>
 * <ol>
 *   <li>Classpath scanning — discovers {@link Component @Component} classes
 *       and validates they are concrete types.</li>
 *   <li>Sorting — orders components by {@link DependsOn @DependsOn}
 *       constraints then {@link Order @Order} priority.</li>
 *   <li>Construction — instantiates each component via
 *       {@link ConstructorResolver}, resolving constructor dependencies.</li>
 *   <li>Field injection — injects {@link Inject @Inject} fields via
 *       {@link FieldResolver}.</li>
 *   <li>{@link PostConstruct @PostConstruct} — per-component initialization
 *       after all fields are injected.</li>
 *   <li>Cache warming — builds the assignable-type lookup cache.</li>
 *   <li>{@link ApplicationReady @ApplicationReady} — fired once the
 *       container is fully wired and cached.</li>
 * </ol>
 *
 * <p>Shutdown reverses the process: {@link PreDestroy @PreDestroy} while
 * the container is still intact, then container cleanup, then
 * {@link PostDestroy @PostDestroy} after the container is gone.</p>
 */
public class Application {

    private static final List<Class<? extends Annotation>> ANNOTATION_CLASS_LIST = List.of(Component.class, Service.class, Repository.class);

    @Getter
    private static ComponentContainer componentContainer;

    /**
     * Initializes the dependency injection container by scanning the
     * package of the given root class and wiring all discovered components.
     *
     * @param rootClass the class whose package serves as the scan root
     * @throws InjectorException if the application has already been
     *                           initialized or a non-concrete type is
     *                           annotated with {@link Component @Component}
     */
    public static void initialize(final Class<?> rootClass) {
        if (getComponentContainer() != null) {
            throw new InjectorException("Application has already been initialized.");
        }

        componentContainer = new ComponentContainer();

        final List<Class<?>> componentClassList = scanComponents(rootClass.getPackageName());

        final List<Class<?>> sortedComponentClassList = ComponentSorter.sort(componentClassList);

        for (final Class<?> type : sortedComponentClassList) {
            getComponentContainer().registerComponentClass(type);
        }

        final ConstructorResolver constructorResolver = new ConstructorResolver(getComponentContainer());
        final FieldResolver fieldResolver = new FieldResolver(getComponentContainer());

        for (final Class<?> type : getComponentContainer().getComponentClassList()) {
            if (getComponentContainer().isInstance(type)) {
                continue;
            }

            constructorResolver.create(type);
        }

        for (final Object instance : getComponentContainer().getInstanceList()) {
            fieldResolver.inject(instance);
        }

        invokeLifecycle(PostConstruct.class);

        getComponentContainer().buildCache();

        invokeLifecycle(ApplicationReady.class);
    }

    /**
     * Shuts down the application by invoking destroy callbacks and
     * clearing the container.
     *
     * <p>{@link PreDestroy @PreDestroy} methods are called while the
     * container is still available. The container is then cleared and
     * nulled. {@link PostDestroy @PostDestroy} methods are called on
     * the saved instance references after the container is gone.</p>
     *
     * @param rootClass the same root class used during initialization
     */
    public static void shutdown(final Class<?> rootClass) {
        if (getComponentContainer() == null) {
            return;
        }

        invokeLifecycle(PreDestroy.class);

        final List<Object> instanceList = getComponentContainer().getInstanceList();

        getComponentContainer().clear();
        componentContainer = null;

        for (final Object instance : instanceList) {
            invokeAnnotatedMethods(instance, PostDestroy.class);
        }
    }

    /**
     * Scans the given package for {@link Component @Component}-annotated
     * classes, including meta-annotations such as {@code @Service} and
     * {@code @Repository}, and validates that each is a concrete type.
     * Interfaces, abstract classes, enums, records, and annotations are rejected.
     *
     * @param basePackage the package to scan
     * @return an unmodifiable, deduplicated list of valid component classes
     * @throws InjectorException if a non-concrete type is annotated
     */
    private static List<Class<?>> scanComponents(final String basePackage) {
        final Reflections reflections = new Reflections(basePackage, Scanners.TypesAnnotated);

        final Set<Class<?>> componentClassSet = UtilJava.createCollection(new HashSet<>(), list -> {
            for (final Class<? extends Annotation> clazz : ANNOTATION_CLASS_LIST) {
                list.addAll(reflections.getTypesAnnotatedWith(clazz, false));
            }
        });

        return Collections.unmodifiableList(UtilJava.createCollection(new ArrayList<>(), list -> {
            for (final Class<?> type : componentClassSet) {
                if (type.isInterface()) {
                    throw new InjectorException("@%s cannot be applied to interfaces: %s".formatted(Component.class.getSimpleName(), type.getName()));
                }

                if (Modifier.isAbstract(type.getModifiers())) {
                    throw new InjectorException("@%s cannot be applied to abstract classes: %s".formatted(Component.class.getSimpleName(), type.getName()));
                }

                if (type.isEnum()) {
                    throw new InjectorException("@%s cannot be applied to enums: %s".formatted(Component.class.getSimpleName(), type.getName()));
                }

                if (type.isRecord()) {
                    throw new InjectorException("@%s cannot be applied to records: %s".formatted(Component.class.getSimpleName(), type.getName()));
                }

                if (type.isAnnotation()) {
                    throw new InjectorException("@%s cannot be applied to annotations: %s".formatted(Component.class.getSimpleName(), type.getName()));
                }

                if (!(list.contains(type))) {
                    list.add(type);
                }
            }
        }));
    }

    /**
     * Invokes all methods annotated with the given lifecycle annotation
     * across every instance in the container.
     *
     * @param annotation the lifecycle annotation to invoke
     */
    private static void invokeLifecycle(final Class<? extends Annotation> annotation) {
        for (final Object instance : getComponentContainer().getInstanceList()) {
            invokeAnnotatedMethods(instance, annotation);
        }
    }

    /**
     * Reflectively invokes all methods on the given instance that carry
     * the specified annotation, walking the full class hierarchy.
     *
     * @param instance   the component instance to invoke methods on
     * @param annotation the annotation to look for
     * @throws InjectorException if an annotated method has parameters
     *                           or invocation fails
     */
    private static void invokeAnnotatedMethods(final Object instance, final Class<? extends Annotation> annotation) {
        Class<?> clazz = instance.getClass();

        while (clazz != null && clazz != Object.class) {
            for (final Method method : clazz.getDeclaredMethods()) {
                if (!(method.isAnnotationPresent(annotation))) {
                    continue;
                }

                if (method.getParameterCount() != 0) {
                    throw new InjectorException("@%s method must have no parameters: %s.%s".formatted(annotation.getSimpleName(), clazz.getName(), method.getName()));
                }

                try {
                    method.setAccessible(true);
                    method.invoke(instance);
                } catch (final Exception e) {
                    throw new InjectorException("Failed to invoke @%s method: %s.%s".formatted(annotation.getSimpleName(), clazz.getName(), method.getName()), e);
                }
            }

            clazz = clazz.getSuperclass();
        }
    }
}