package io.github.trae.di.annotations.type;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as an application entry point for the dependency
 * injection framework.
 *
 * <p>The annotated class's package is used as the base package for
 * classpath scanning. All {@link io.github.trae.di.annotations.type.component.Component @Component}-annotated
 * classes discovered under that package are registered and managed
 * by the container.</p>
 *
 * <p>Multi-application projects declare their upstream dependencies via
 * {@link #dependencies()}. The framework resolves the full dependency
 * tree, initializes applications in topological order, and wires all
 * components into a single shared container. This means a downstream
 * application can inject components from any of its declared (or transitive)
 * dependencies without manual registration.</p>
 *
 * <pre>{@code
 * @Application
 * public class ProjectOne {}
 *
 * @Application(dependencies = ProjectOne.class)
 * public class ProjectTwo {}
 * }</pre>
 *
 * @see io.github.trae.di.InjectorApi
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Application {

    /**
     * Other {@code @Application}-annotated classes that this application
     * depends on. Their packages are scanned and their components
     * are initialized before this application's components.
     *
     * @return the upstream application classes (empty by default)
     */
    Class<?>[] dependencies() default {};
}