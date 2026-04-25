# Dependency Injector

A lightweight and flexible dependency injection framework for Java.

Dependency Injector provides a simple container for managing object creation and wiring dependencies automatically using constructor injection, field injection, and lifecycle callbacks.

The framework is designed to be lightweight, fast, and easy to integrate into existing Java applications.

---

## Features

- Automatic classpath scanning via `@Component` with meta-annotation support
- Stereotype annotations `@Service` and `@Repository` for semantic clarity
- `@Configuration` POJOs with JSON and YAML support, in-place reload and save
- `@Comment` annotations for injecting human-readable comments into config files
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
- Scheduled tasks via `@Scheduler` with fixed-rate, clock-aligned, synchronous/asynchronous modes and platform executor hooks
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
</dependency>

<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
</dependency>

<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
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

### Configuration

Use `@Configuration` to define config POJOs that are automatically loaded, injected, and support in-place reload. No base class is required — any POJO works. Field names are used directly as keys — static and transient fields are ignored.

JSON is the default format. Use `ConfigType.YAML` for YAML:

```java
@Configuration("database")                              // JSON (default)
@Configuration(value = "database", type = ConfigType.YAML)  // YAML
```

Set the configuration directory before initialization:

```java
@Application
public class Main {

    public static void main(final String[] args) {
        InjectorApi.setConfigurationDirectory(Path.of("configs"));
        InjectorApi.initialize(Main.class);
    }
}
```

Define a config class — field defaults become the initial file values:

```java
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Configuration("database")
public class DatabaseConfig {

    private String host = "localhost";
    private int port = 3306;
    private String username = "root";
    private String password = "changeme";
    private boolean debugMode = false;
}
```

On first run, this creates `configs/database.json`:

```json
{
  "host": "localhost",
  "port": 3306,
  "username": "root",
  "password": "changeme",
  "debugMode": false
}
```

Or with `ConfigType.YAML`, it creates `configs/database.yml`:

```yaml
host: localhost
port: 3306
username: root
password: changeme
debugMode: false
```

#### Config Comments

Use `@Comment` to add descriptive comments above fields in the generated config files. Comments are injected on every save and initial creation. Supported for both JSON and YAML formats. Multi-line comments are supported via string arrays — each entry becomes a separate comment line.

```java
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Configuration("database")
public class DatabaseConfig {

    @Comment("The hostname or IP address of the database server.")
    private String host = "localhost";

    @Comment("The port the database server is listening on.")
    private int port = 3306;

    @Comment({"The username for database authentication.", "Must have read and write permissions."})
    private String username = "root";

    @Comment("The password for database authentication.")
    private String password = "changeme";

    @Comment("Enable verbose query logging for debugging.")
    private boolean debugMode = false;
}
```

This generates `configs/database.json`:

```json
{
  // The hostname or IP address of the database server.
  "host": "localhost",
  // The port the database server is listening on.
  "port": 3306,
  // The username for database authentication.
  // Must have read and write permissions.
  "username": "root",
  // The password for database authentication.
  "password": "changeme",
  // Enable verbose query logging for debugging.
  "debugMode": false
}
```

Or with `ConfigType.YAML`:

```yaml
# The hostname or IP address of the database server.
host: localhost
# The port the database server is listening on.
port: 3306
# The username for database authentication.
# Must have read and write permissions.
username: root
# The password for database authentication.
password: changeme
# Enable verbose query logging for debugging.
debugMode: false
```

Config instances are registered into the container and injectable like any other component:

```java
@AllArgsConstructor
@Service
public class DatabaseService {

    private final DatabaseConfig databaseConfig;

    @PostConstruct
    public void onPostConstruct() {
        System.out.println("Connecting to " + databaseConfig.getHost() + ":" + databaseConfig.getPort());
    }
}
```

Reload and save operations are handled centrally via `InjectorApi`. The existing instance in the container is updated in-place, so any component holding a reference will see the new values immediately:

```java
// Reload a single config from disk
InjectorApi.reloadConfiguration(DatabaseConfig.class);

// Reload all configs from disk
InjectorApi.reloadConfigurations();

// Save a single config to disk
InjectorApi.saveConfiguration(DatabaseConfig.class);
```

### Multi-Application

Multiple applications can share a single container. Declare upstream dependencies with `@Application(dependencies = ...)` and each application initializes independently. The container is additive — downstream applications can inject components from any upstream application.

```java
// Core project
@Application
public class CorePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        InjectorApi.initialize(this);
    }

    @Override
    public void onDisable() {
        InjectorApi.shutdown(this);
    }
}

// Factions project — depends on Core
@Application(dependencies = CorePlugin.class)
public class FactionsPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        InjectorApi.initialize(this);
    }

    @Override
    public void onDisable() {
        InjectorApi.shutdown(this);
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

InjectorApi.initialize(this);
```

Comparators are chained in registration order — each one acts as a tiebreaker for the previous phase. This is the mechanism used by the [Hierarchy-Framework](https://github.com/Trae-Maven/hierarchy-framework) to ensure Managers initialize before Modules before SubModules.

### Execute Callback

Use `InjectorApi.executeCallback(...)` to iterate all components belonging to a specific application and execute logic against each instance. Call it after `initialize()` to register components with external systems, and before `shutdown()` to unregister them.

```java
@Application
public class CorePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        InjectorApi.initialize(this);

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

        InjectorApi.shutdown(this);
    }
}
```

For platforms with shared boilerplate like Bukkit, you can extract the callback logic into an abstract base class. Every plugin that extends it gets automatic listener and command registration with zero per-plugin configuration:

```java
public abstract class SpigotPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        InjectorApi.initialize(this);

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

        InjectorApi.shutdown(this);
    }
}

@Application
public class CorePlugin extends SpigotPlugin {}

@Application(dependencies = CorePlugin.class)
public class FactionsPlugin extends SpigotPlugin {}
```

### Scheduled Tasks

Use `@Scheduler` to mark a no-argument method as a repeating task. The method is registered after the container is fully wired and continues to execute until the owning application is shut down. Each application manages its own scheduled tasks independently — shutting down one application does not affect another's schedulers.

```java
@Component
public class MetricsService {

    @Scheduler(period = 30, unit = TimeUnit.SECONDS)
    public void flushMetrics() {
        // fires every 30 seconds from application start
    }
}
```

#### Initial Delay

Use `initialDelay` to control how long the task waits before its first execution. If not set, it defaults to `period` — so the first tick fires one full interval after registration:

```java
@Scheduler(initialDelay = 5, period = 30, unit = TimeUnit.SECONDS)
public void warmCache() {
    // waits 5 seconds, then fires every 30 seconds
}
```

#### Clock-Aligned Mode

Set `clock = true` to align executions to wall-clock boundaries that are multiples of the period. The first execution is delayed until the next aligned boundary, then repeats at fixed rate. `initialDelay` is ignored in this mode.

For example, a 5-minute period fires at `:00`, `:05`, `:10`, `:15`, etc. regardless of when the application started:

```java
@Component
public class SnapshotService {

    @Scheduler(period = 5, unit = TimeUnit.MINUTES, clock = true)
    public void takeSnapshot() {
        // fires at :00, :05, :10, :15, etc.
    }
}
```

#### Asynchronous Mode

By default, scheduled tasks are dispatched through the synchronous executor (e.g. the game thread). Set `asynchronous = true` to dispatch through the asynchronous executor instead. If no platform executors are set, both modes run on the internal scheduler thread pool:

```java
@Component
public class AnalyticsService {

    // Runs on the platform's main thread (default)
    @Scheduler(period = 5, unit = TimeUnit.SECONDS)
    public void updateScoreboard() {
        // ...
    }

    // Runs on the platform's asynchronous thread pool
    @Scheduler(period = 30, unit = TimeUnit.SECONDS, asynchronous = true)
    public void flushAnalytics() {
        // ...
    }

    // Clock-aligned, runs on the platform's main thread
    @Scheduler(period = 1, unit = TimeUnit.MINUTES, clock = true)
    public void synchronousLeaderboard() {
        // ...
    }

    // Clock-aligned, runs on the platform's asynchronous thread pool
    @Scheduler(period = 1, unit = TimeUnit.MINUTES, clock = true, asynchronous = true)
    public void pushMetrics() {
        // ...
    }
}
```

#### Platform Executors

Use `InjectorApi.setSynchronousExecutor(...)` and `InjectorApi.setAsynchronousExecutor(...)` to hook into the platform's threading model. Set these before `initialize()`:

```java
@Application
public class CorePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        InjectorApi.setSynchronousExecutor(runnable -> Bukkit.getScheduler().runTask(this, runnable));
        InjectorApi.setAsynchronousExecutor(runnable -> Bukkit.getScheduler().runTaskAsynchronously(this, runnable));
        InjectorApi.initialize(this);
    }

    @Override
    public void onDisable() {
        InjectorApi.shutdown(this);
    }
}
```

If neither executor is set, all tasks run on the internal `di-scheduler` daemon thread pool — no platform integration required. This makes the framework usable outside game platforms (e.g. standalone Spring Boot applications) without setting either hook.

A single component can have multiple scheduled methods with different intervals and modes:

```java
@Component
public class MonitoringService {

    @Scheduler(period = 10, unit = TimeUnit.SECONDS)
    public void pollHealth() {
        // fixed-rate from application start
    }

    @Scheduler(period = 1, unit = TimeUnit.MINUTES, clock = true)
    public void reportMetrics() {
        // clock-aligned to the start of each minute
    }
}
```

The backing thread pool is lazily initialized — no threads are allocated if an application has no `@Scheduler` methods. During shutdown, all scheduled tasks are cancelled before `@PreDestroy` methods are invoked.

### Initialization and Shutdown Order

During initialization, components are constructed and wired in sorted order — dependencies first, then priority, then any registered comparators. During shutdown, components are destroyed in the reverse of their initialization order so that children are torn down before their parents.

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
| `@Configuration` | Class | Marks a class as a config POJO with JSON/YAML support, in-place reload and save |
| `@Comment` | Field | Adds comment lines above the field in the serialized config file |
| `@SoftDependency` | Class | Conditionally registers a component based on runtime classpath availability |
| `@Inject` | Field | Injects a dependency from the container |
| `@Order` | Class | Controls initialization priority (lower = earlier) |
| `@DependsOn` | Class | Declares explicit initialization dependencies |
| `@PostConstruct` | Method | Invoked after construction and field injection |
| `@ApplicationReady` | Method | Invoked after the container is fully wired |
| `@PreDestroy` | Method | Invoked during shutdown, container is still available |
| `@PostDestroy` | Method | Invoked during shutdown, container has been destroyed |
| `@Scheduler` | Method | Repeating scheduled task with fixed-rate, clock-aligned, and synchronous/asynchronous modes |
