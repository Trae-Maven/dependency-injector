# Dependency Injector

A lightweight and flexible dependency injection framework for Java.

Dependency Injector provides a simple container for managing object creation and wiring dependencies automatically using constructor injection, field injection, and lifecycle callbacks.

The framework is designed to be lightweight, fast, and easy to integrate into existing Java applications.

---

## Features

- Automatic classpath scanning via `@Component` with meta-annotation support
- Stereotype annotations `@Service` and `@Repository` for semantic clarity
- Multi-application support via `@Application` with dependency resolution across projects
- Additive container — each application initializes independently and shares a single container
- Conditional component registration via `@SoftDependency` for optional runtime dependencies
- Per-application component callbacks via `executeCallback` for platform integration
- Constructor injection with greedy constructor selection
- Field injection via `@Inject` with full class hierarchy traversal
- Collection injection (`List`, `Set`) for both constructors and fields
- Explicit dependency ordering via `@DependsOn` with circular detection
- Priority-based initialization via `@Order`
- Composable `ComponentComparator` extension point for external sorting logic
- Reverse-order shutdown — components are destroyed in the opposite order they were initialized
- Lifecycle callbacks: `@PostConstruct`, `@ApplicationReady`, `@PreDestroy`, `@PostDestroy`
- Circular dependency detection at both annotation and runtime level
- Lightweight dependency container with assignable-type lookups
- Minimal dependencies
- Designed for modern Java (Java 21+)
- Easy integration into existing projects

---

## Requirements

Dependency-Injector has no external runtime dependencies.

The following is only needed at compile time for annotation processing:
```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.36</version>
    <scope>provided</scope>
</dependency>
```

---

## Built-in Dependencies

Dependency Injector includes several internal helper utilities used throughout the framework.

- [Utilities](https://github.com/Trae-Maven/utilities) – Shared helper classes and performance-focused utilities used internally by the framework.
```xml
<dependency>
    <groupId>org.reflections</groupId>
    <artifactId>reflections</artifactId>
    <version>0.10.2</version>
</dependency>
```

These dependencies are included automatically through Maven and do not need to be installed separately.

---

## Installation

Add the dependency to your Maven project:
```xml
<dependency>
    <groupId>io.github.trae</groupId>
    <artifactId>dependency-injector</artifactId>
    <version>0.0.1</version>
</dependency>
```

---

## Quick Start

### Single Application

Mark your entry point with `@Application` and bootstrap with `InjectorApi`:
```java
@Application
public class Main {

    public static void main(final String[] args) {
        InjectorApi.initialize(Main.class);
    }
}
```

Constructor injection is the preferred approach. The container automatically selects the constructor with the most parameters and resolves all dependencies. This works naturally with Lombok's `@AllArgsConstructor`.
```java
@AllArgsConstructor
@Repository
public class UserRepository {

    private final DatabaseService databaseService;
}

@AllArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;

    @PostConstruct
    public void onPostConstruct() {
        // called after all fields are injected
    }

    @ApplicationReady
    public void onApplicationReady() {
        // called after the entire container is wired
    }

    @PreDestroy
    public void onPreDestroy() {
        // called during shutdown, container is still available
    }

    @PostDestroy
    public void onPostDestroy() {
        // called during shutdown, container has been destroyed
    }
}
```

Field injection via `@Inject` is also supported for cases where constructor injection is not practical. Injected fields must not be declared `final`, as they are assigned reflectively after construction.
```java
@Service
public class OrderService {

    @Inject
    private UserService userService;

    @Inject
    private List<PaymentHandler> paymentHandlers;
}
```

### Multi-Application

Multiple applications can share a single container. Declare upstream dependencies with `@Application(dependencies = ...)` and each application initializes independently. The container is additive — downstream applications can inject components from any upstream application.
```java
// Core project
@Application
public class CorePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        InjectorApi.initialize(CorePlugin.class);
    }

    @Override
    public void onDisable() {
        InjectorApi.shutdown(CorePlugin.class);
    }
}

// Factions project — depends on Core
@Application(dependencies = CorePlugin.class)
public class FactionsPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        InjectorApi.initialize(FactionsPlugin.class);
    }

    @Override
    public void onDisable() {
        InjectorApi.shutdown(FactionsPlugin.class);
    }
}
```

Components in Factions can inject components from Core via constructor or field injection:
```java
@AllArgsConstructor
@Component
public class FactionManager {

    private final PlayerManager playerManager; // from Core
}
```

### Soft Dependencies

Use `@SoftDependency` to conditionally register a component based on whether an external library is present on the runtime classpath. If any of the specified packages are not found, the component is skipped entirely — it is never registered, constructed, or injected.

No Maven dependency is required — the check is purely at runtime against whatever JARs are loaded on the classpath.
```java
@SoftDependency("com.stripe.api")
@Component
public class StripePaymentService {}
```

Multiple packages can be specified — all must be present for the component to be registered:
```java
@SoftDependency({"com.rabbitmq.client", "io.lettuce.core"})
@Component
public class MessageBrokerAdapter {}
```

### Component Comparators

Use `ComponentSorter.addComparator(...)` to register custom sorting logic that runs after the default `@DependsOn` and `@Order` phases. This allows external frameworks to influence initialization order without modifying the core DI.
```java
ComponentSorter.addComparator((a, b) -> {
    return Integer.compare(getPriority(a), getPriority(b));
});

InjectorApi.initialize(CorePlugin.class);
```

Comparators are chained in registration order — each one acts as a tiebreaker for the previous phase. This is the mechanism used by the [Hierarchy-Framework](https://github.com/Trae-Maven/hierarchy-framework) to ensure Managers initialize before Modules before SubModules.

### Execute Callback

Use `InjectorApi.executeCallback(...)` to iterate all components belonging to a specific application and execute logic against each instance. Call it after `initialize()` to register components with external systems, and before `shutdown()` to unregister them.
```java
@Application
public class CorePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        InjectorApi.initialize(CorePlugin.class);

        InjectorApi.executeCallback(CorePlugin.class, instance -> {
            if (instance instanceof Listener listener) {
                Bukkit.getServer().getPluginManager().registerEvents(listener, this);
            }

            if (instance instanceof Command command) {
                CommandHandler.registerCommand(command);
            }
        });
    }

    @Override
    public void onDisable() {
        InjectorApi.executeCallback(CorePlugin.class, instance -> {
            if (instance instanceof Listener listener) {
                HandlerList.unregisterAll(listener);
            }

            if (instance instanceof Command command) {
                CommandHandler.unregisterCommand(command);
            }
        });

        InjectorApi.shutdown(CorePlugin.class);
    }
}
```

For platforms with shared boilerplate like Bukkit, you can extract the callback logic into an abstract base class. Every plugin that extends it gets automatic listener and command registration with zero per-plugin configuration:
```java
public abstract class SpigotPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        InjectorApi.initialize(this.getClass());

        InjectorApi.executeCallback(this.getClass(), instance -> {
            if (instance instanceof Listener listener) {
                Bukkit.getServer().getPluginManager().registerEvents(listener, this);
            }
        });
    }

    @Override
    public void onDisable() {
        InjectorApi.executeCallback(this.getClass(), instance -> {
            if (instance instanceof Listener listener) {
                HandlerList.unregisterAll(listener);
            }
        });

        InjectorApi.shutdown(this.getClass());
    }
}

@Application
public class CorePlugin extends SpigotPlugin {}

@Application(dependencies = CorePlugin.class)
public class FactionsPlugin extends SpigotPlugin {}
```

### Initialization and Shutdown Order

During initialization, components are constructed and wired in sorted order — dependencies first, then priority, then any registered comparators. During shutdown, components are destroyed in the reverse of their initialization order so that children are torn down before their parents.

### Retrieving Components

Use `InjectorApi.get(...)` to retrieve any component from any application:
```java
final PlayerManager playerManager = InjectorApi.get(PlayerManager.class);
```

Use `InjectorApi.getComponentClassListByApplication(...)` to retrieve the component classes registered by a specific application:
```java
final List<Class<?>> coreComponents = InjectorApi.getComponentClassListByApplication(CorePlugin.class);
final List<Class<?>> factionsComponents = InjectorApi.getComponentClassListByApplication(FactionsPlugin.class);
```

---

## Annotations

| Annotation | Target | Description |
|---|---|---|
| `@Application` | Class | Marks a class as an application entry point with optional dependencies |
| `@Component` | Class | Marks a class as a managed singleton |
| `@Service` | Class | Stereotype for service-layer components |
| `@Repository` | Class | Stereotype for data-access components |
| `@SoftDependency` | Class | Conditionally registers a component based on runtime classpath availability |
| `@Inject` | Field | Injects a dependency from the container |
| `@Order` | Class | Controls initialization priority (lower = earlier) |
| `@DependsOn` | Class | Declares explicit initialization dependencies |
| `@PostConstruct` | Method | Invoked after construction and field injection |
| `@ApplicationReady` | Method | Invoked after the container is fully wired |
| `@PreDestroy` | Method | Invoked during shutdown, container is still available |
| `@PostDestroy` | Method | Invoked during shutdown, container has been destroyed |
