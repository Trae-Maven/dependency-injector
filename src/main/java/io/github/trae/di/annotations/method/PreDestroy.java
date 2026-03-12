package io.github.trae.di.annotations.method;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Invoked during shutdown before the container is cleared.
 *
 * <p>The annotated method must accept no parameters. At invocation time
 * the container is still intact, so other components can still be
 * accessed. Use this for graceful cleanup such as closing connections,
 * flushing buffers, or deregistering listeners.</p>
 *
 * <pre>{@code
 * @Component
 * public class DatabaseService {
 *
 *     @PreDestroy
 *     public void onPreDestroy() {
 *         // container is still available
 *     }
 * }
 * }</pre>
 *
 * @see PostDestroy
 * @see PostConstruct
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PreDestroy {
}