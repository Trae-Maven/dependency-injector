package io.github.trae.di.annotations.type;

import io.github.trae.di.annotations.type.component.Singleton;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controls the initialization priority of a {@link Singleton}.
 *
 * <p>Components with a lower value are initialized before those with a higher
 * value. Components without {@code @Order} default to {@link Integer#MAX_VALUE},
 * meaning they are initialized last. Ordering is applied after
 * {@link DependsOn} constraints have been resolved.</p>
 *
 * <pre>{@code
 * @Order(1)
 * @Singleton
 * public class DatabaseService {
 *     // initialized early
 * }
 *
 * @Order(10)
 * @Singleton
 * public class CacheService {
 *     // initialized after DatabaseService
 * }
 * }</pre>
 *
 * @see DependsOn
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Order {

    int value() default 0;
}