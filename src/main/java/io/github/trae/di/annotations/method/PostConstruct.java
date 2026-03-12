package io.github.trae.di.annotations.method;

import io.github.trae.di.annotations.field.Inject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Invoked after a component has been constructed and all of its
 * {@link Inject} fields have been resolved.
 *
 * <p>The annotated method must accept no parameters. It is called once
 * per component during the initialization phase, before the container
 * is considered ready. Use this for setup logic that depends on
 * injected dependencies being available.</p>
 *
 * <pre>{@code
 * @AllArgsConstructor
 * @Component
 * public class CacheService {
 *
 *     private final DatabaseService databaseService;
 *
 *     @PostConstruct
 *     public void onPostConstruct() {
 *         // databaseService is guaranteed to be injected
 *     }
 * }
 * }</pre>
 *
 * @see ApplicationReady
 * @see PreDestroy
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PostConstruct {
}