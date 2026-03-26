package io.github.trae.di.configuration.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adds a comment above the annotated field in the serialized
 * configuration file. Supported for both JSON and YAML formats.
 *
 * <p>For JSON, comments are injected as {@code //} lines and
 * stripped during deserialization. For YAML, comments are injected
 * as standard {@code #} lines which are natively ignored by the
 * parser.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Comment {

    /**
     * The comment lines to place above the field.
     *
     * @return the comment lines
     */
    String[] value();
}