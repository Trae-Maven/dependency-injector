package io.github.trae.di.annotations.type.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specialization of {@link Component} for service-layer classes.
 *
 * <p>Functionally identical to {@code @Component}. Use this to
 * semantically distinguish business logic and service classes
 * from other managed components.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Service {
}