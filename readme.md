# Dependency Injector

A lightweight and flexible dependency injection framework for Java.

Dependency Injector provides a simple container for managing object creation and wiring dependencies automatically using constructor injection, field injection, and lifecycle callbacks.

The framework is designed to be lightweight, fast, and easy to integrate into existing Java applications.

---

## Features

- Automatic classpath scanning via `@Component` with meta-annotation support
- Stereotype annotations `@Service` and `@Repository` for semantic clarity
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

// Bootstrap
Application.initialize(Main.class);

// Shutdown
Application.shutdown(Main.class);
```

Field injection via `@Inject` is also supported for cases where constructor injection is not practical.
```java
@Service
public class OrderService {

    @Inject
    private UserService userService;

    @Inject
    private List<PaymentHandler> paymentHandlers;
}
```

---

## Annotations

| Annotation | Target | Description |
|---|---|---|
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
