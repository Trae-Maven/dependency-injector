package io.github.trae.di;

import io.github.trae.di.annotations.field.Inject;
import io.github.trae.di.annotations.method.ApplicationReady;
import io.github.trae.di.annotations.method.PostConstruct;
import io.github.trae.di.annotations.method.PostDestroy;
import io.github.trae.di.annotations.method.PreDestroy;
import io.github.trae.di.annotations.type.Application;
import io.github.trae.di.annotations.type.DependsOn;
import io.github.trae.di.annotations.type.Order;
import io.github.trae.di.annotations.type.component.Component;
import io.github.trae.di.annotations.type.component.Repository;
import io.github.trae.di.annotations.type.component.Service;
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
 * <p>Manages the full application lifecycle across one or more
 * {@link Application @Application}-annotated applications:</p>
 * <ol>
 *   <li>Application resolution — reads {@link Application @Application}
 *       annotations and topologically sorts the dependency tree so
 *       upstream applications are initialized before downstream ones.</li>
 *   <li>Classpath scanning — discovers {@link Component @Component} classes
 *       in each application's package and validates they are concrete types.
 *       Applications already initialized are skipped.</li>
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
 * <p>The container is additive — each call to {@link #initialize(Class)}
 * adds new components on top of any previously initialized applications.
 * This allows independent plugins to boot in sequence via their own
 * {@code onEnable()} while sharing a single container.</p>
 *
 * <p>Shutdown reverses the process: {@link PreDestroy @PreDestroy} while
 * the container is still intact, then container cleanup, then
 * {@link PostDestroy @PostDestroy} after the container is gone.</p>
 */
public class InjectorApi {

    private static final List<Class<? extends Annotation>> ANNOTATION_CLASS_LIST = List.of(Component.class, Service.class, Repository.class);

    /**
     * Tracks which {@link Application @Application}-annotated classes
     * have already been initialized, preventing duplicate scanning
     * when a downstream application declares an already-booted upstream
     * application as a dependency.
     */
    private static final Set<Class<?>> initializedApplicationSet = new HashSet<>();

    /**
     * Maps each {@link Application @Application}-annotated class to
     * the component classes that were registered during its initialization.
     */
    private static final Map<Class<?>, List<Class<?>>> applicationComponentMap = new LinkedHashMap<>();

    @Getter
    private static ComponentContainer componentContainer;

    /**
     * Returns the singleton instance of the given component type from
     * the shared container. Works across all initialized applications —
     * any application can retrieve any component regardless of which
     * application registered it.
     *
     * <pre>{@code
     * final ClientManager clientManager = InjectorApi.get(ClientManager.class);
     * }</pre>
     *
     * @param type the component class to look up
     * @param <T>  the component type
     * @return the singleton instance
     * @throws InjectorException if the container has not been initialized
     */
    public static <T> T get(final Class<T> type) {
        if (getComponentContainer() == null) {
            throw new InjectorException("Application has not been initialized.");
        }

        return getComponentContainer().getInstance(type);
    }

    /**
     * Returns the component classes that were registered by the given
     * {@link Application @Application}-annotated class during its
     * initialization.
     *
     * <pre>{@code
     * final List<Class<?>> projectOneComponents = InjectorApi.getComponentClassListByApplication(ProjectOne.class);
     * final List<Class<?>> projectTwoComponents = InjectorApi.getComponentClassListByApplication(ProjectTwo.class);
     * }</pre>
     *
     * @param applicationClass the {@code @Application}-annotated class
     * @return an unmodifiable list of component classes registered by
     * that application, or an empty list if the application
     * has not been initialized
     */
    public static List<Class<?>> getComponentClassListByApplication(final Class<?> applicationClass) {
        return Collections.unmodifiableList(applicationComponentMap.getOrDefault(applicationClass, Collections.emptyList()));
    }

    /**
     * Initializes the dependency injection container by resolving the
     * {@link Application @Application} dependency tree rooted at the given
     * class, scanning all application packages in dependency order, and
     * wiring all discovered components into the shared container.
     *
     * <p>Applications that have already been initialized are skipped,
     * allowing each plugin to call {@code initialize()} independently
     * during its own startup. The container is created on the first
     * call and reused for all subsequent calls.</p>
     *
     * @param rootClass the {@code @Application}-annotated entry point class
     * @throws InjectorException if the root class is not annotated,
     *                           a circular application dependency is detected,
     *                           or a non-concrete type is annotated with
     *                           {@link Component @Component}
     */
    public static void initialize(final Class<?> rootClass) {
        if (!(rootClass.isAnnotationPresent(Application.class))) {
            throw new InjectorException("Root class must be annotated with @%s: %s".formatted(Application.class.getSimpleName(), rootClass.getName()));
        }

        if (componentContainer == null) {
            componentContainer = new ComponentContainer();
        }

        final List<Class<?>> applicationOrderList = resolveApplicationOrder(rootClass);

        final List<Class<?>> newComponentClassList = new ArrayList<>();

        for (final Class<?> applicationClass : applicationOrderList) {
            if (initializedApplicationSet.contains(applicationClass)) {
                continue;
            }

            final List<Class<?>> scannedComponentClassList = scanComponents(applicationClass.getPackageName());

            final List<Class<?>> sortedComponentClassList = ComponentSorter.sort(scannedComponentClassList);

            final List<Class<?>> applicationNewClassList = new ArrayList<>();

            for (final Class<?> type : sortedComponentClassList) {
                if (!(getComponentContainer().getComponentClassList().contains(type))) {
                    getComponentContainer().registerComponentClass(type);
                    newComponentClassList.add(type);
                    applicationNewClassList.add(type);
                }
            }

            applicationComponentMap.put(applicationClass, applicationNewClassList);
            initializedApplicationSet.add(applicationClass);
        }

        final ConstructorResolver constructorResolver = new ConstructorResolver(getComponentContainer());
        final FieldResolver fieldResolver = new FieldResolver(getComponentContainer());

        for (final Class<?> type : newComponentClassList) {
            if (getComponentContainer().isInstance(type)) {
                continue;
            }

            constructorResolver.create(type);
        }

        for (final Class<?> type : newComponentClassList) {
            final Object instance = getComponentContainer().getInstance(type);

            fieldResolver.inject(instance);
        }

        for (final Class<?> type : newComponentClassList) {
            final Object instance = getComponentContainer().getInstance(type);

            invokeAnnotatedMethods(instance, PostConstruct.class);
        }

        getComponentContainer().buildCache();

        for (final Class<?> type : newComponentClassList) {
            final Object instance = getComponentContainer().getInstance(type);

            invokeAnnotatedMethods(instance, ApplicationReady.class);
        }
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
        initializedApplicationSet.clear();
        applicationComponentMap.clear();

        for (final Object instance : instanceList) {
            invokeAnnotatedMethods(instance, PostDestroy.class);
        }
    }

    /**
     * Resolves the full {@link Application @Application} dependency tree
     * rooted at the given class using a depth-first topological sort.
     *
     * <p>The result is ordered so that dependencies appear before the
     * applications that depend on them — guaranteeing upstream components
     * are scanned and registered first.</p>
     *
     * @param rootClass the entry point application class
     * @return a list of application classes in initialization order
     * @throws InjectorException if a dependency is not annotated with
     *                           {@code @Application} or a circular
     *                           dependency is detected
     */
    private static List<Class<?>> resolveApplicationOrder(final Class<?> rootClass) {
        final LinkedHashSet<Class<?>> resolved = new LinkedHashSet<>();
        final Set<Class<?>> visiting = new HashSet<>();

        visitApplication(rootClass, resolved, visiting);

        return new ArrayList<>(resolved);
    }

    /**
     * Recursively visits an {@link Application @Application}-annotated
     * class and its dependencies, building the topologically sorted
     * result. Tracks classes currently on the call stack via
     * {@code visiting} to detect circular dependencies.
     *
     * @param applicationClass the application class being visited
     * @param resolved         the accumulated result in dependency order
     * @param visiting         applications currently on the recursion stack
     * @throws InjectorException if a dependency is not annotated with
     *                           {@code @Application} or a cycle is detected
     */
    private static void visitApplication(final Class<?> applicationClass, final LinkedHashSet<Class<?>> resolved, final Set<Class<?>> visiting) {
        if (resolved.contains(applicationClass)) {
            return;
        }

        if (visiting.contains(applicationClass)) {
            throw new InjectorException("Circular @%s dependency detected: %s".formatted(Application.class.getSimpleName(), applicationClass.getName()));
        }

        visiting.add(applicationClass);

        final Application annotation = applicationClass.getAnnotation(Application.class);

        if (annotation == null) {
            throw new InjectorException("@%s dependency must be annotated with @%s: %s".formatted(
                    Application.class.getSimpleName(), Application.class.getSimpleName(), applicationClass.getName()));
        }

        for (final Class<?> dependency : annotation.dependencies()) {
            visitApplication(dependency, resolved, visiting);
        }

        visiting.remove(applicationClass);
        resolved.add(applicationClass);
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