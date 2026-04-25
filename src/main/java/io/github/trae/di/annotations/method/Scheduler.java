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
 * <p>The method first waits {@link #initialDelay()} (defaulting to
 * {@link #period()} if unset), then executes at a fixed rate of
 * {@link #period()} {@link #unit()} from when the scheduler was
 * started.</p>
 *
 * <h3>Clock-aligned mode ({@code clock = true})</h3>
 * <p>The method executes at wall-clock boundaries that are multiples
 * of the configured period. For example, a 5-minute period will
 * fire at {@code :00}, {@code :05}, {@code :10}, {@code :15}, etc.
 * regardless of when the application started. {@link #initialDelay()}
 * is ignored in this mode — the first execution is always delayed
 * until the next aligned boundary.</p>
 *
 * <pre>{@code
 * @Component
 * public class MetricsService {
 *
 *     // Fires every 30 seconds, aligned to :00 and :30 of each minute
 *     @Scheduler(period = 30, unit = TimeUnit.SECONDS, clock = true)
 *     public void flushMetrics() {
 *         // ...
 *     }
 *
 *     // Waits 5 seconds, then fires every 10 seconds from application start
 *     @Scheduler(initialDelay = 5, period = 10, unit = TimeUnit.SECONDS)
 *     public void pollHealth() {
 *         // ...
 *     }
 *
 *     // Waits 10 seconds (defaults to period), then fires every 10 seconds
 *     @Scheduler(period = 10, unit = TimeUnit.SECONDS)
 *     public void checkQueue() {
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
     * The delay before the first execution. Only applies in standard
     * mode ({@code clock = false}). A value of {@code 0} (the default)
     * means the initial delay equals {@link #period()}.
     *
     * @return the initial delay value
     */
    int initialDelay() default 0;

    /**
     * The interval between executions.
     *
     * @return the period value
     */
    int period();

    /**
     * The time unit for {@link #initialDelay()} and {@link #period()}.
     *
     * @return the time unit
     */
    TimeUnit unit();

    /**
     * When {@code true}, executions are aligned to wall-clock
     * boundaries that are exact multiples of the period since
     * the epoch. For example, a 5-minute period fires at
     * {@code :00}, {@code :05}, {@code :10}, etc.
     * {@link #initialDelay()} is ignored in this mode.
     *
     * <p>When {@code false} (the default), the task runs at a
     * fixed rate relative to when the scheduler was started.</p>
     *
     * @return {@code true} for clock-aligned scheduling
     */
    boolean clock() default false;
}