package io.github.trae.di.annotations.type.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a singleton component.
 *
 * <p>Classes annotated with {@code @Singleton} are managed by the dependency
 * container with a single instance created and shared for the lifetime of the
 * container.</p>
 *
 * <p>Custom stereotype annotations meta-annotated with {@code @Singleton} are
 * discovered the same way, with no registration required.</p>
 *
 * <pre>{@code
 * @Singleton
 * public class UserService {
 *     // single instance managed by the container
 * }
 * }</pre>
 *
 * @see Repository
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Singleton {
}