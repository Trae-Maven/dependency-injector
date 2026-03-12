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
- Constructor injection with greedy constructor selection
- Field injection via `@Inject` with full class hierarchy traversal
- Collection injection (`List`, `Set`) for both constructors and fields
- Explicit dependency ordering via `@DependsOn` with circular detection
- Priority-based initialization via `@Order`
- Lifecycle callbacks: `@PostConstruct`, `@ApplicationReady`, `@PreDestroy`, `@PostDestroy`
- Circular dependency detection at both annotation and runtime level
- Lightweight dependency container with assignable-type lookups
- Minimal dependencies
- Designed for modern Java (Java 21+)
- Easy integration into existing projects

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

Field injection via `@Inject` is also supported for cases where constructor injection is not practical. Injected fields must not be declared `final`, as they are assigned reflectively after construction.```java
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
| `@Inject` | Field | Injects a dependency from the container |
| `@Order` | Class | Controls initialization priority (lower = earlier) |
| `@DependsOn` | Class | Declares explicit initialization dependencies |
| `@PostConstruct` | Method | Invoked after construction and field injection |
| `@ApplicationReady` | Method | Invoked after the container is fully wired |
| `@PreDestroy` | Method | Invoked during shutdown, container is still available |
| `@PostDestroy` | Method | Invoked during shutdown, container has been destroyed |
