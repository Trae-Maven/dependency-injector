package io.github.trae.di.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares explicit initialization dependencies between components.
 *
 * <p>The container guarantees that all classes listed in {@code values}
 * are fully constructed before the annotated component is created.
 * Circular {@code @DependsOn} references are detected at startup and
 * result in a {@link io.github.trae.di.exceptions.DependencyException}.
 * All referenced classes must themselves be registered components.</p>
 *
 * <pre>{@code
 * @DependsOn(DatabaseService.class, CacheService.class)
 * @Component
 * public class UserRepository {
 * }
 * }</pre>
 *
 * @see Order
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DependsOn {

    Class<?>[] values();
}