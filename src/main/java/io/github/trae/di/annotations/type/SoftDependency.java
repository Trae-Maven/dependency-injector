package io.github.trae.di.annotations.type;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Conditionally registers a component based on whether one or more
 * external packages exist on the runtime classpath.
 *
 * <p>During classpath scanning, if any of the specified packages
 * cannot be found at runtime, the annotated component is skipped
 * entirely — it is never registered, constructed, or injected.</p>
 *
 * <p>This is intended for adapting to optional external libraries
 * that are not part of the project's own dependency tree. No Maven
 * dependency is required — the check is purely at runtime against
 * whatever JARs are loaded on the classpath.</p>
 *
 * <p>Multiple packages can be specified — all must be present for
 * the component to be registered.</p>
 *
 * <pre>{@code
 * @SoftDependency("com.stripe.api")
 * @Component
 * public class StripePaymentService {
 * }
 * }</pre>
 *
 * @see io.github.trae.di.annotations.type.component.Component
 * @see io.github.trae.di.InjectorApi
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SoftDependency {

    /**
     * Base package names that must exist on the runtime classpath
     * for this component to be registered. If any package cannot
     * be found, the component is skipped.
     *
     * @return the required package names
     */
    String[] value();
}