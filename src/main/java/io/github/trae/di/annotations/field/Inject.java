package io.github.trae.di.annotations.field;

import io.github.trae.di.annotations.type.component.Singleton;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field for dependency injection.
 *
 * <p>When the container initializes a {@link Singleton}, every field annotated
 * with {@code @Inject} is resolved from the container and assigned after
 * construction. The field type may be a concrete class, an interface, or a
 * supported collection ({@link java.util.List} or {@link java.util.Set})
 * parameterized with a component type.</p>
 *
 * <pre>{@code
 * @Singleton
 * public class OrderService {
 *
 *     @Inject
 *     private UserService userService;
 *
 *     @Inject
 *     private List<PaymentHandler> paymentHandlers;
 * }
 * }</pre>
 *
 * @see Singleton
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Inject {
}