package io.github.trae.di.annotations.type.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specialization of {@link Component} for small, lightweight managed classes.
 *
 * <p>Functionally identical to {@code @Component}. Use this to semantically
 * distinguish small, focused components — such as simple value holders,
 * helpers, adapters, or single-purpose utilities — from larger service-layer
 * or domain classes.</p>
 *
 * <pre>{@code
 * @Bean
 * public class ClockProvider {
 *     // small, single-purpose component
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Bean {
}