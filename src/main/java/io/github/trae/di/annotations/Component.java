package io.github.trae.di.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a managed component.
 *
 * <p>Classes annotated with {@code @Component} are automatically discovered
 * during classpath scanning and registered as singletons in the dependency
 * container. Only concrete classes may be annotated; interfaces, abstract
 * classes, enums, records, and annotations are rejected at startup.</p>
 *
 * <pre>{@code
 * @Component
 * public class UserService {
 *     // managed by the container
 * }
 * }</pre>
 *
 * @see Inject
 * @see Order
 * @see DependsOn
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Component {
}