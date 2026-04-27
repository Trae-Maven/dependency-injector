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
import io.github.trae.di.annotations.type.component.Service;
import io.github.trae.di.configuration.annotations.Configuration;
import io.github.trae.di.containers.ComponentContainer;
import io.github.trae.di.exceptions.DependencyException;
import io.github.trae.di.exceptions.InjectorException;
import io.github.trae.di.resolvers.ConfigurationResolver;
import io.github.trae.di.resolvers.ConstructorResolver;
import io.github.trae.di.resolvers.FieldResolver;
import io.github.trae.di.resolvers.SchedulerResolver;
import io.github.trae.di.sorters.ComponentSorter;
import io.github.trae.utilities.UtilJava;
import lombok.Getter;
import lombok.Setter;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
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
 *       their required packages are not present on the runtime classpath.
 *       Custom stereotype annotations meta-annotated with {@code @Component},
 *       {@code @Service}, or {@code @Configuration} are automatically
 *       discovered without any additional registration.</li>
 *   <li>Sorting — orders components by {@link DependsOn @DependsOn}
 *       constraints, then {@link Order @Order} priority, then any registered
 *       {@link io.github.trae.di.sorters.comparators.ComponentComparator ComponentComparators}.</li>
 *   <li>Configuration — resolves all {@link Configuration @Configuration}
 *       classes first so they are available before any component constructor runs.</li>
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
 * Components are destroyed in reverse initialization order so that
 * children are torn down before their parents.
 * {@link PreDestroy @PreDestroy} and {@link PostDestroy @PostDestroy}
 * are invoked only for that application's components. When the last
 * application shuts down, the container is cleared.</p>
 */
public class InjectorApi {

    private static final List<Class<? extends Annotation>> ANNOTATION_CLASS_LIST = List.of(Component.class, Service.class, Configuration.class);

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
     * Maps each {@link Application @Application}-annotated class to its
     * configuration directory. Set via
     * {@link #setConfigurationDirectory(Class, Path)} before
     * {@link #initialize(Class)} so that each application's
     * {@link Configuration @Configuration} files are resolved from
     * the correct directory.
     */
    private static final Map<Class<?>, Path> configurationDirectoryMap = new LinkedHashMap<>();

    /**
     * The shared configuration resolver, created during initialization if
     * a configuration directory is set. Retained so that configurations
     * can be reloaded and saved after initialization.
     */
    @Getter
    private static ConfigurationResolver configurationResolver;

    /**
     * Maps each {@link Application @Application}-annotated class to its
     * {@link SchedulerResolver}, so scheduled tasks can be shut down
     * per-application.
     */
    private static final Map<Class<?>, SchedulerResolver> schedulerResolverMap = new LinkedHashMap<>();

    /**
     * Optional executor for dispatching {@link io.github.trae.di.annotations.method.Scheduler @Scheduler}
     * tasks with {@code asynchronous = false} onto the platform's main thread
     * (e.g. Bukkit's {@code runTask}, Hytale's server scheduler).
     *
     * <p>If not set, synchronous tasks fall back to running on the
     * internal scheduler thread pool.</p>
     */
    @Getter
    @Setter
    private static Consumer<Runnable> synchronousExecutor;

    /**
     * Optional executor for dispatching {@link io.github.trae.di.annotations.method.Scheduler @Scheduler}
     * tasks with {@code asynchronous = true} onto the platform's asynchronous
     * thread pool (e.g. Bukkit's {@code runTaskAsynchronously}).
     *
     * <p>If not set, asynchronous tasks run on the internal scheduler
     * thread pool.</p>
     */
    @Getter
    @Setter
    private static Consumer<Runnable> asynchronousExecutor;

    /**
     * Registers a configuration directory for the given
     * {@link Application @Application}-annotated class. Must be called
     * before {@link #initialize(Class)} so that any
     * {@link Configuration @Configuration} classes discovered during
     * that application's initialization are resolved from the correct
     * directory.
     *
     * @param applicationClass the {@code @Application}-annotated class
     * @param path             the directory where config files are stored
     */
    public static void setConfigurationDirectory(final Class<?> applicationClass, final Path path) {
        configurationDirectoryMap.put(applicationClass, path);
    }

    /**
     * Returns the configuration directory registered for the given
     * {@link Application @Application}-annotated class, or {@code null}
     * if none has been set.
     *
     * @param applicationClass the {@code @Application}-annotated class
     * @return the configuration directory, or {@code null}
     */
    public static Path getConfigurationDirectory(final Class<?> applicationClass) {
        return configurationDirectoryMap.get(applicationClass);
    }

    /**
     * Initializes the dependency injection container using only the
     * {@link Application @Application}-annotated class. The class is used
     * for package scanning and dependency resolution but is not registered
     * as an instance in the container.
     *
     * <p>Use {@link #initialize(Object)} instead when an existing instance
     * of the application class should be injectable by other components.</p>
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

        initialize(rootClass, null);
    }

    /**
     * Initializes the dependency injection container and registers the
     * given application instance into the container so it can be injected
     * by other components.
     *
     * <p>The instance's class must be annotated with
     * {@link Application @Application}. This is the preferred overload
     * for platform plugins where the runtime creates the instance
     * externally (e.g. Hytale's plugin loader, Bukkit's JavaPlugin).</p>
     *
     * @param rootInstance the {@code @Application}-annotated instance
     * @throws InjectorException if the instance's class is not annotated,
     *                           a circular application dependency is detected,
     *                           or a non-concrete type is annotated with
     *                           {@link Component @Component}
     */
    public static void initialize(final Object rootInstance) {
        if (rootInstance == null) {
            throw new IllegalArgumentException("Root Instance cannot be null.");
        }

        initialize(rootInstance.getClass(), rootInstance);
    }

    /**
     * Core initialization logic shared by both {@link #initialize(Class)}
     * and {@link #initialize(Object)}.
     *
     * <p>If {@code rootInstance} is non-null, it is registered into the
     * container under its concrete class before any component construction
     * begins, making it available for injection.</p>
     *
     * @param rootClass    the {@code @Application}-annotated class
     * @param rootInstance the application instance to register, or {@code null}
     */
    private static void initialize(final Class<?> rootClass, final Object rootInstance) {
        if (!(rootClass.isAnnotationPresent(Application.class))) {
            throw new InjectorException("Root Class must be annotated with @%s: %s".formatted(Application.class.getSimpleName(), rootClass.getName()));
        }

        if (applicationComponentMap.containsKey(rootClass)) {
            throw new InjectorException("@%s has already been initialized: %s".formatted(Application.class.getSimpleName(), rootClass.getName()));
        }

        if (componentContainer == null) {
            componentContainer = new ComponentContainer();
        }

        // Register the application instance if provided
        if (rootInstance != null) {
            getComponentContainer().registerInstance(rootClass, rootInstance);
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

        final Path configDirectory = configurationDirectoryMap.get(rootClass);
        if (configurationResolver == null && configDirectory != null) {
            configurationResolver = new ConfigurationResolver(getComponentContainer(), configDirectory);
        }

        final ConstructorResolver constructorResolver = new ConstructorResolver(getComponentContainer());
        final FieldResolver fieldResolver = new FieldResolver(getComponentContainer());

        // Pass 1 — resolve all @Configuration classes first so they are
        // available in the container before any component tries to inject them
        for (final Class<?> type : newComponentClassList) {
            if (getComponentContainer().isInstance(type)) {
                continue;
            }

            if (type.isAnnotationPresent(Configuration.class)) {
                if (configurationResolver == null) {
                    throw new InjectorException("Configuration directory not set. Call InjectorApi.setConfigurationDirectory() before initialize().");
                }
                configurationResolver.resolve(type);
            }
        }

        // Pass 2 — construct all remaining components with dependencies resolved
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

        final SchedulerResolver schedulerResolver = new SchedulerResolver(getComponentContainer(), synchronousExecutor, asynchronousExecutor);

        for (final Class<?> type : newComponentClassList) {
            final Object instance = getComponentContainer().getInstance(type);

            schedulerResolver.register(instance);
        }

        schedulerResolverMap.put(rootClass, schedulerResolver);
    }

    /**
     * Shuts down the specified application by invoking destroy callbacks
     * and removing its components from the container.
     *
     * <p>Components are processed in reverse initialization order so that
     * children are torn down before their parents. {@link PreDestroy @PreDestroy}
     * methods are called while the container is still available. The components
     * are then removed from the container. {@link PostDestroy @PostDestroy}
     * methods are called on the saved instance references after removal.</p>
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

        final List<Class<?>> componentClassList = new ArrayList<>(applicationComponentMap.getOrDefault(rootClass, Collections.emptyList()));

        Collections.reverse(componentClassList);

        final List<Object> instanceList = new ArrayList<>();

        final SchedulerResolver schedulerResolver = schedulerResolverMap.remove(rootClass);

        if (schedulerResolver != null) {
            schedulerResolver.shutdown();
        }

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

        // Unregister the application instance if it was registered
        if (getComponentContainer().isInstance(rootClass)) {
            getComponentContainer().unregisterInstance(rootClass);
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
            configurationResolver = null;
            synchronousExecutor = null;
            asynchronousExecutor = null;
        }
    }

    /**
     * Shuts down the application represented by the given instance.
     * Convenience overload that extracts the class and delegates to
     * {@link #shutdown(Class)}.
     *
     * @param rootInstance the same application instance used during initialization
     */
    public static void shutdown(final Object rootInstance) {
        if (rootInstance == null) {
            throw new IllegalArgumentException("Root Instance cannot be null.");
        }

        shutdown(rootInstance.getClass());
    }

    /**
     * Returns the singleton instance of the given component type from
     * the shared container. Works across all initialized applications —
     * any application can retrieve any component regardless of which
     * application registered it.
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
     * Reloads a single {@link Configuration @Configuration} from disk.
     * The existing instance in the container is updated in-place, so any
     * component holding a reference will see the new values immediately.
     *
     * @param type the {@code @Configuration}-annotated class to reload
     * @throws InjectorException if the class is not annotated with
     *                           {@code @Configuration}, the configuration
     *                           resolver is not initialized, or the type
     *                           is not a registered configuration
     */
    public static void reloadConfiguration(final Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null.");
        }

        if (!(type.isAnnotationPresent(Configuration.class))) {
            throw new InjectorException("Class must be annotated with @%s: %s".formatted(Configuration.class.getSimpleName(), type.getName()));
        }

        if (configurationResolver == null) {
            throw new InjectorException("Configuration resolver has not been initialized.");
        }

        configurationResolver.reload(type);
    }

    /**
     * Reloads all registered {@link Configuration @Configuration} instances
     * from disk. Each existing instance is updated in-place.
     *
     * @throws InjectorException if the configuration resolver is not initialized
     */
    public static void reloadConfigurations() {
        if (configurationResolver == null) {
            throw new InjectorException("Configuration resolver has not been initialized.");
        }

        configurationResolver.reloadAll();
    }

    /**
     * Saves a single {@link Configuration @Configuration} to disk using
     * the format specified by {@link Configuration#type()}.
     *
     * @param type the {@code @Configuration}-annotated class to save
     * @throws InjectorException if the class is not annotated with
     *                           {@code @Configuration}, the configuration
     *                           resolver is not initialized, or the type
     *                           is not a registered configuration
     */
    public static void saveConfiguration(final Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null.");
        }

        if (!(type.isAnnotationPresent(Configuration.class))) {
            throw new InjectorException("Class must be annotated with @%s: %s".formatted(Configuration.class.getSimpleName(), type.getName()));
        }

        if (configurationResolver == null) {
            throw new InjectorException("Configuration resolver has not been initialized.");
        }

        configurationResolver.save(type);
    }

    /**
     * Returns the component classes that were registered by the given
     * {@link Application @Application}-annotated class during its
     * initialization.
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
     * Checks whether the given type is annotated — directly or via a
     * meta-annotation — with any annotation in {@link #ANNOTATION_CLASS_LIST}.
     *
     * <p>This walks one level of meta-annotation depth: for each annotation
     * present on the type, its own annotations are checked against the
     * known component annotations. This allows custom stereotype annotations
     * such as {@code @Repository} (which is meta-annotated with
     * {@code @Component}) to be automatically discovered without registering
     * them in the framework.</p>
     *
     * @param type the class to check
     * @return {@code true} if the type is a component (directly or via meta-annotation)
     */
    private static boolean isComponentAnnotated(final Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null.");
        }

        for (final Class<? extends Annotation> annotationClass : ANNOTATION_CLASS_LIST) {
            if (type.isAnnotationPresent(annotationClass)) {
                return true;
            }
        }

        for (final Annotation annotation : type.getAnnotations()) {
            for (final Class<? extends Annotation> annotationClass : ANNOTATION_CLASS_LIST) {
                if (annotation.annotationType().isAnnotationPresent(annotationClass)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Resolves the annotation name for error messages by checking which
     * annotation from {@link #ANNOTATION_CLASS_LIST} is present on the
     * given type, including meta-annotations one level deep.
     *
     * @param type the class to check
     * @return the simple name of the matched annotation
     * @throws DependencyException if no known annotation is present
     */
    private static String resolveAnnotationName(final Class<?> type) {
        for (final Class<? extends Annotation> annotationClass : ANNOTATION_CLASS_LIST) {
            if (type.isAnnotationPresent(annotationClass)) {
                return annotationClass.getSimpleName();
            }
        }

        for (final Annotation annotation : type.getAnnotations()) {
            for (final Class<? extends Annotation> annotationClass : ANNOTATION_CLASS_LIST) {
                if (annotation.annotationType().isAnnotationPresent(annotationClass)) {
                    return annotation.annotationType().getSimpleName();
                }
            }
        }

        throw new DependencyException("Could not resolve annotation type for %s".formatted(type.getName()));
    }

    /**
     * Scans the given package for classes annotated with any known component
     * annotation — either directly ({@link Component @Component},
     * {@link Service @Service}, {@link Configuration @Configuration}) or via
     * a meta-annotation (e.g. {@code @Repository} which is itself annotated
     * with {@code @Component}).
     *
     * <p>Discovery is performed in two passes:</p>
     * <ol>
     *   <li><b>Direct annotations</b> — uses
     *       {@link Reflections#getTypesAnnotatedWith(Class, boolean)} for each
     *       annotation in {@link #ANNOTATION_CLASS_LIST}. This catches all
     *       classes directly annotated with {@code @Component},
     *       {@code @Service}, or {@code @Configuration}.</li>
     *   <li><b>Meta-annotations</b> — iterates the raw
     *       {@link Scanners#TypesAnnotated} store values to retrieve every
     *       annotated class name indexed within the scanned package. Each
     *       class is loaded using the classloader from a class already
     *       resolved by pass 1 (so that isolated plugin classloaders are
     *       honoured), and checked via {@link #isComponentAnnotated(Class)}
     *       which walks one level of meta-annotation depth. This catches
     *       classes annotated with custom stereotype annotations (e.g.
     *       {@code @Repository}) whose annotation type lives <em>outside</em>
     *       the scanned package but is itself meta-annotated with a known
     *       component annotation. Classes already discovered by pass 1 are
     *       skipped.</li>
     * </ol>
     *
     * <p>Only concrete classes are accepted — interfaces, abstract classes,
     * enums, records, and annotations are silently skipped or rejected.
     * Components annotated with {@link SoftDependency @SoftDependency} are
     * skipped if any of their required packages are not found on the runtime
     * classpath.</p>
     *
     * @param basePackage the package to scan
     * @return an unmodifiable, deduplicated list of valid component classes
     * @throws InjectorException if a non-concrete type is annotated with a
     *                           component annotation
     */
    private static List<Class<?>> scanComponents(final String basePackage) {
        if (basePackage == null) {
            throw new IllegalArgumentException("Base Package cannot be null.");
        }

        final Reflections reflections = new Reflections(basePackage, Scanners.TypesAnnotated);

        final Set<Class<?>> componentClassSet = UtilJava.createCollection(new HashSet<>(), set -> {
            // Pass 1 — direct annotations (original proven behavior)
            for (final Class<? extends Annotation> clazz : ANNOTATION_CLASS_LIST) {
                set.addAll(reflections.getTypesAnnotatedWith(clazz, false));
            }

            // Resolve the classloader from a class already loaded by pass 1,
            // falling back to the context classloader if pass 1 found nothing.
            // This ensures classes in isolated plugin classloaders can be loaded.
            final ClassLoader classLoader = set.isEmpty()
                    ? Thread.currentThread().getContextClassLoader()
                    : set.iterator().next().getClassLoader();

            // Pass 2 — meta-annotations: iterate the raw TypesAnnotated store
            // values (annotated class names) and check if any carry a
            // meta-annotated stereotype (e.g. @Repository -> @Component).
            final Map<String, Set<String>> store = reflections.getStore().getOrDefault(Scanners.TypesAnnotated.index(), Collections.emptyMap());

            for (final Set<String> classNameSet : store.values()) {
                for (final String className : classNameSet) {
                    if (set.stream().anyMatch(c -> c.getName().equals(className))) {
                        continue;
                    }

                    try {
                        final Class<?> clazz = Class.forName(className, false, classLoader);

                        if (isComponentAnnotated(clazz)) {
                            set.add(clazz);
                        }
                    } catch (final ClassNotFoundException ignored) {
                    }
                }
            }
        });

        return Collections.unmodifiableList(UtilJava.createCollection(new ArrayList<>(), list -> {
            for (final Class<?> type : componentClassSet) {
                if (!(isComponentAnnotated(type))) {
                    continue;
                }

                if (type.isInterface()) {
                    throw new InjectorException("@%s cannot be applied to interfaces: %s".formatted(resolveAnnotationName(type), type.getName()));
                }

                if (Modifier.isAbstract(type.getModifiers())) {
                    throw new InjectorException("@%s cannot be applied to abstract classes: %s".formatted(resolveAnnotationName(type), type.getName()));
                }

                if (type.isEnum()) {
                    throw new InjectorException("@%s cannot be applied to enums: %s".formatted(resolveAnnotationName(type), type.getName()));
                }

                if (type.isRecord()) {
                    throw new InjectorException("@%s cannot be applied to records: %s".formatted(resolveAnnotationName(type), type.getName()));
                }

                if (type.isAnnotation()) {
                    continue;
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