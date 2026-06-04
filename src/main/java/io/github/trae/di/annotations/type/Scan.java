package io.github.trae.di.annotations.type;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares one or more base packages to be scanned for components.
 *
 * <p>May be placed on any type in an {@link io.github.trae.di.annotations.type.Application @Application}
 * class's hierarchy — the concrete application class itself, a superclass,
 * or an implemented interface. The {@link io.github.trae.di.resolvers.ScanResolver ScanResolver}
 * walks the full superclass and interface graph of the bootstrap type and
 * collects every {@code @Scan} it finds, so each framework layer can declare
 * the packages it owns and have them discovered automatically.</p>
 *
 * <p>For example, a framework interface can carry
 * {@code @Scan("io.github.trae.hf")}; a concrete application implementing
 * that interface inherits the package in addition to its own, without
 * declaring anything itself.</p>
 *
 * <p>When {@link #value()} is empty, the package of the annotated type itself
 * is scanned. When one or more packages are given, those packages are scanned
 * instead and the annotated type's own package is ignored.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Scan {

    /**
     * The base packages to scan. If empty, the package of the annotated
     * type is used instead.
     *
     * @return the base packages, or an empty array to scan the annotated
     * type's own package
     */
    String[] value() default {};
}