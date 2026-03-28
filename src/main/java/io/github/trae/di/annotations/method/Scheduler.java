package io.github.trae.di.annotations.method;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Marks a no-argument method as a scheduled task that executes
 * repeatedly at a fixed interval.
 *
 * <p>The annotated method must accept no parameters. It is registered
 * during the {@link ApplicationReady} phase and continues to execute
 * until the owning application is shut down.</p>
 *
 * <h3>Standard mode ({@code clock = false})</h3>
 * <p>The method executes at a fixed rate relative to when the
 * scheduler was started — i.e. every {@link #delay()} {@link #unit()}
 * from the moment the application becomes ready.</p>
 *
 * <h3>Clock-aligned mode ({@code clock = true})</h3>
 * <p>The method executes at wall-clock boundaries that are multiples
 * of the configured interval. For example, a 5-minute interval will
 * fire at {@code :00}, {@code :05}, {@code :10}, {@code :15}, etc.
 * regardless of when the application started. The first execution is
 * delayed until the next aligned boundary.</p>
 *
 * <pre>{@code
 * @Component
 * public class MetricsService {
 *
 *     // Fires every 30 seconds, aligned to :00 and :30 of each minute
 *     @Scheduler(delay = 30, unit = TimeUnit.SECONDS, clock = true)
 *     public void flushMetrics() {
 *         // ...
 *     }
 *
 *     // Fires every 10 seconds from application start
 *     @Scheduler(delay = 10, unit = TimeUnit.SECONDS)
 *     public void pollHealth() {
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * @see ApplicationReady
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Scheduler {

    /**
     * The interval between executions.
     *
     * @return the delay value
     */
    int delay();

    /**
     * The time unit for {@link #delay()}.
     *
     * @return the time unit
     */
    TimeUnit unit();

    /**
     * When {@code true}, executions are aligned to wall-clock
     * boundaries that are exact multiples of the interval since
     * the epoch. For example, a 5-minute interval fires at
     * {@code :00}, {@code :05}, {@code :10}, etc.
     *
     * <p>When {@code false} (the default), the task runs at a
     * fixed rate relative to when the scheduler was started.</p>
     *
     * @return {@code true} for clock-aligned scheduling
     */
    boolean clock() default false;
}