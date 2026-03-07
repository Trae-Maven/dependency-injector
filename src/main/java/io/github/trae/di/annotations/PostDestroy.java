package io.github.trae.di.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Invoked during shutdown after the container has been cleared.
 *
 * <p>The annotated method must accept no parameters. At invocation time
 * the container no longer exists, so other components cannot be resolved.
 * Use this for final teardown that must happen after the container is
 * gone, such as releasing native resources or logging shutdown
 * completion.</p>
 *
 * <pre>{@code
 * @Component
 * public class NativeResourceHandler {
 *
 *     @PostDestroy
 *     public void onPostDestroy() {
 *         // container has been destroyed
 *     }
 * }
 * }</pre>
 *
 * @see PreDestroy
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PostDestroy {
}