package io.github.trae.di;

import io.github.trae.di.annotations.field.Inject;
import io.github.trae.di.annotations.method.ApplicationReady;
import io.github.trae.di.annotations.method.PostConstruct;
import io.github.trae.di.annotations.method.PostDestroy;
import io.github.trae.di.annotations.method.PreDestroy;
import io.github.trae.di.annotations.type.Application;
import io.github.trae.di.annotations.type.DependsOn;
import io.github.trae.di.annotations.type.Order;
import io.github.trae.di.annotations.type.SoftDependency;
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
import java.util.function.Consumer;

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
 *       Applications already initialized are skipped. Components annotated
 *       with {@link SoftDependency @SoftDependency} are skipped if any of
 *       their required packages are not present on the runtime classpath.</li>
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
 * <p>Shutdown is scoped per application — {@link #shutdown(Class)} only
 * tears down the components belonging to that specific application.
 * {@link PreDestroy @PreDestroy} and {@link PostDestroy @PostDestroy}
 * are invoked only for that application's components. When the last
 * application shuts down, the container is cleared.</p>
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
        if (rootClass == null) {
            throw new IllegalArgumentException("Root Class cannot be null.");
        }

        if (!(rootClass.isAnnotationPresent(Application.class))) {
            throw new InjectorException("Root Class must be annotated with @%s: %s".formatted(Application.class.getSimpleName(), rootClass.getName()));
        }

        if (applicationComponentMap.containsKey(rootClass)) {
            throw new InjectorException("@%s has already been initialized: %s".formatted(Application.class.getSimpleName(), rootClass.getName()));
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
     * Shuts down the specified application by invoking destroy callbacks
     * and removing its components from the container.
     *
     * <p>{@link PreDestroy @PreDestroy} methods are called on the
     * application's components while the container is still available.
     * The components are then removed from the container.
     * {@link PostDestroy @PostDestroy} methods are called on the saved
     * instance references after removal.</p>
     *
     * <p>When the last application shuts down, the container itself
     * is cleared and nulled.</p>
     *
     * @param rootClass the same root class used during initialization
     */
    public static void shutdown(final Class<?> rootClass) {
        if (rootClass == null) {
            throw new IllegalArgumentException("Root Class cannot be null.");
        }

        if (!(applicationComponentMap.containsKey(rootClass))) {
            throw new InjectorException("@%s has not been initialized: %s".formatted(Application.class.getSimpleName(), rootClass.getName()));
        }

        if (getComponentContainer() == null) {
            return;
        }

        final List<Class<?>> componentClassList = applicationComponentMap.getOrDefault(rootClass, Collections.emptyList());

        final List<Object> instanceList = new ArrayList<>();

        for (final Class<?> type : componentClassList) {
            if (!(getComponentContainer().isInstance(type))) {
                continue;
            }

            final Object instance = getComponentContainer().getInstance(type);

            invokeAnnotatedMethods(instance, PreDestroy.class);

            instanceList.add(instance);
        }

        for (final Class<?> type : componentClassList) {
            getComponentContainer().unregisterInstance(type);
            getComponentContainer().unregisterComponentClass(type);
        }

        applicationComponentMap.remove(rootClass);
        initializedApplicationSet.remove(rootClass);

        getComponentContainer().buildCache();

        for (final Object instance : instanceList) {
            invokeAnnotatedMethods(instance, PostDestroy.class);
        }

        if (initializedApplicationSet.isEmpty()) {
            getComponentContainer().clear();
            componentContainer = null;
        }
    }

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
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null.");
        }

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
     * final List<Class<?>> coreComponents = InjectorApi.getComponentClassListByApplication(CorePlugin.class);
     * final List<Class<?>> clansComponents = InjectorApi.getComponentClassListByApplication(ClansPlugin.class);
     * }</pre>
     *
     * @param applicationClass the {@code @Application}-annotated class
     * @return an unmodifiable list of component classes registered by
     * that application, or an empty list if the application
     * has not been initialized
     */
    public static List<Class<?>> getComponentClassListByApplication(final Class<?> applicationClass) {
        if (applicationClass == null) {
            throw new IllegalArgumentException("Application Class cannot be null.");
        }

        return Collections.unmodifiableList(applicationComponentMap.getOrDefault(applicationClass, Collections.emptyList()));
    }

    /**
     * Executes a callback against all components belonging to the
     * specified application. The consumer is invoked immediately for
     * each of the application's component instances.
     *
     * <p>This is the primary mechanism for platform integration — use
     * it after {@link #initialize(Class)} to register components with
     * external systems, and before {@link #shutdown(Class)} to
     * unregister them.</p>
     *
     * <pre>{@code
     * InjectorApi.initialize(CorePlugin.class);
     *
     * // Register listeners and commands after initialization
     * InjectorApi.executeCallback(CorePlugin.class, instance -> {
     *     if (instance instanceof Listener listener) {
     *         Bukkit.getServer().getPluginManager().registerEvents(listener, this);
     *     }
     *
     *     if (instance instanceof Command command) {
     *         CommandHandler.registerCommand(command);
     *     }
     * });
     * }</pre>
     *
     * @param applicationClass the {@code @Application}-annotated class
     *                         to scope the callback to
     * @param callback         the consumer to invoke for each component
     */
    public static void executeCallback(final Class<?> applicationClass, final Consumer<Object> callback) {
        if (applicationClass == null) {
            throw new IllegalArgumentException("Application Class cannot be null.");
        }

        if (callback == null) {
            throw new IllegalArgumentException("Callback cannot be null.");
        }

        if (getComponentContainer() == null) {
            throw new InjectorException("Application has not been initialized.");
        }

        final List<Class<?>> componentClassList = applicationComponentMap.getOrDefault(applicationClass, Collections.emptyList());

        for (final Class<?> componentClass : componentClassList) {
            if (!(getComponentContainer().isInstance(componentClass))) {
                continue;
            }

            final Object instance = getComponentContainer().getInstance(componentClass);

            callback.accept(instance);
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
        if (rootClass == null) {
            throw new IllegalArgumentException("Root Class cannot be null.");
        }

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
        if (applicationClass == null) {
            throw new IllegalArgumentException("Application Class cannot be null.");
        }

        if (resolved == null) {
            throw new IllegalArgumentException("Resolved Class cannot be null.");
        }

        if (visiting == null) {
            throw new IllegalArgumentException("Visiting cannot be null.");
        }

        if (resolved.contains(applicationClass)) {
            return;
        }

        if (visiting.contains(applicationClass)) {
            throw new InjectorException("Circular @%s dependency detected: %s".formatted(Application.class.getSimpleName(), applicationClass.getName()));
        }

        visiting.add(applicationClass);

        final Application annotation = applicationClass.getAnnotation(Application.class);

        if (annotation == null) {
            throw new InjectorException("@%s dependency must be annotated with @%s: %s".formatted(Application.class.getSimpleName(), Application.class.getSimpleName(), applicationClass.getName()));
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
     * Interfaces, abstract classes, enums, records, and annotations are
     * rejected. Components annotated with {@link SoftDependency @SoftDependency}
     * are skipped if any of their required packages are not found on the
     * runtime classpath.
     *
     * @param basePackage the package to scan
     * @return an unmodifiable, deduplicated list of valid component classes
     * @throws InjectorException if a non-concrete type is annotated
     */
    private static List<Class<?>> scanComponents(final String basePackage) {
        if (basePackage == null) {
            throw new IllegalArgumentException("Base Package cannot be null.");
        }

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

                if (type.isAnnotationPresent(SoftDependency.class)) {
                    if (!(isSoftDependencyAvailable(type.getAnnotation(SoftDependency.class)))) {
                        continue;
                    }
                }

                if (!(list.contains(type))) {
                    list.add(type);
                }
            }
        }));
    }

    /**
     * Checks whether all packages specified by the given
     * {@link SoftDependency @SoftDependency} annotation are present
     * on the runtime classpath.
     *
     * <p>Each package is checked by converting the package name to a
     * resource path and looking it up via the current thread's context
     * class loader. This works for any JAR loaded at runtime, regardless
     * of whether it is a compile-time Maven dependency.</p>
     *
     * @param annotation the {@code @SoftDependency} annotation to check
     * @return {@code true} if all required packages are available,
     * {@code false} if any are missing
     */
    private static boolean isSoftDependencyAvailable(final SoftDependency annotation) {
        if (annotation == null) {
            throw new IllegalArgumentException("Annotation cannot be null");
        }

        for (final String basePackage : annotation.value()) {
            final String path = basePackage.replace('.', '/');

            if (Thread.currentThread().getContextClassLoader().getResource(path) == null) {
                return false;
            }
        }

        return true;
    }

    /**
     * Invokes all methods annotated with the given lifecycle annotation
     * across every instance in the container.
     *
     * @param annotation the lifecycle annotation to invoke
     */
    private static void invokeLifecycle(final Class<? extends Annotation> annotation) {
        if (annotation == null) {
            throw new IllegalArgumentException("Annotation cannot be null.");
        }

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
        if (instance == null) {
            throw new IllegalArgumentException("Instance cannot be null.");
        }

        if (annotation == null) {
            throw new IllegalArgumentException("Annotation cannot be null.");
        }

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