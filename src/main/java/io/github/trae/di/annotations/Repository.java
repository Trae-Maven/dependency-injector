package io.github.trae.di.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specialization of {@link Component} for data-access classes.
 *
 * <p>Functionally identical to {@code @Component}. Use this to
 * semantically distinguish persistence and data-access classes
 * from other managed components.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface Repository {
}