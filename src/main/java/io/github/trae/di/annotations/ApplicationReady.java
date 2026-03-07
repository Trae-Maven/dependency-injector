package io.github.trae.di.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Invoked once all components have been constructed, injected, and
 * post-constructed — signalling that the application is fully ready.
 *
 * <p>The annotated method must accept no parameters. Unlike
 * {@link PostConstruct}, which fires per-component during initialization,
 * {@code @ApplicationReady} fires after the entire container is built
 * and cached. Use this for logic that requires the full object graph
 * to be available.</p>
 *
 * <pre>{@code
 * @Component
 * public class HealthMonitor {
 *
 *     @ApplicationReady
 *     public void onApplicationReady() {
 *         // all components are fully wired
 *     }
 * }
 * }</pre>
 *
 * @see PostConstruct
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ApplicationReady {
}