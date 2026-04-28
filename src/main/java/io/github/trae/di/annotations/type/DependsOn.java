package io.github.trae.di.annotations.type;

import java.lang.annotation.*;

/**
 * Declares explicit initialization dependencies between components.
 *
 * <p>The container guarantees that all classes listed in {@code values}
 * are fully constructed before the annotated component is created.
 * Circular {@code @DependsOn} references are detected at startup and
 * result in a {@link io.github.trae.di.exceptions.DependencyException}.
 * All referenced classes must themselves be registered components.</p>
 *
 * <p>This annotation is {@link Inherited @Inherited}, so subclasses
 * automatically inherit the dependency constraints of their parent.</p>
 *
 * @see Order
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface DependsOn {

    Class<?>[] values();
}