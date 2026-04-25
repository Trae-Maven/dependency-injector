package io.github.trae.di.resolvers;

import io.github.trae.di.annotations.method.Scheduler;
import io.github.trae.di.containers.ComponentContainer;
import io.github.trae.di.exceptions.InjectorException;
import io.github.trae.di.resolvers.abstracts.AbstractResolver;
import io.github.trae.di.resolvers.interfaces.ISchedulerResolver;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Discovers {@link Scheduler @Scheduler}-annotated methods on component
 * instances and registers them with a shared
 * {@link ScheduledExecutorService}.
 *
 * <p>Supports two scheduling modes:</p>
 * <ul>
 *   <li><b>Standard</b> ({@code clock = false}) — the task runs at a
 *       fixed rate relative to when {@link #register(Object)} was called.</li>
 *   <li><b>Clock-aligned</b> ({@code clock = true}) — the first execution
 *       is delayed until the next wall-clock boundary that is an exact
 *       multiple of the configured interval (since the epoch), then
 *       repeats at fixed rate thereafter. For example, a 5-minute
 *       interval fires at {@code :00}, {@code :05}, {@code :10}, etc.</li>
 * </ul>
 *
 * <p>All scheduled futures are tracked so they can be cancelled
 * cleanly during {@link #shutdown()}.</p>
 */
public class SchedulerResolver extends AbstractResolver implements ISchedulerResolver {

    private final List<ScheduledFuture<?>> scheduledFutureArrayList = new ArrayList<>();

    private ScheduledExecutorService executorService;

    /**
     * Creates a new resolver. The backing {@link ScheduledExecutorService}
     * is not created until the first {@link Scheduler @Scheduler}-annotated
     * method is discovered, avoiding unnecessary thread allocation when
     * an application has no scheduled tasks.
     *
     * @param componentContainer the shared component container
     */
    public SchedulerResolver(final ComponentContainer componentContainer) {
        super(componentContainer);
    }

    /**
     * Scans the given instance for {@link Scheduler @Scheduler}-annotated
     * methods and schedules each one. Walks the full class hierarchy.
     *
     * @param instance the component instance to scan
     * @throws InjectorException if an annotated method has parameters
     */
    @Override
    public void register(final Object instance) {
        if (instance == null) {
            throw new IllegalArgumentException("Instance cannot be null.");
        }

        Class<?> clazz = instance.getClass();

        while (clazz != null && clazz != Object.class) {
            for (final Method method : clazz.getDeclaredMethods()) {
                if (!(method.isAnnotationPresent(Scheduler.class))) {
                    continue;
                }

                if (method.getParameterCount() != 0) {
                    throw new InjectorException("@%s method must have no parameters: %s.%s".formatted(Scheduler.class.getSimpleName(), clazz.getName(), method.getName()));
                }

                method.setAccessible(true);

                final Scheduler annotation = method.getAnnotation(Scheduler.class);

                schedule(instance, method, annotation);
            }

            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Cancels all scheduled tasks and shuts down the executor.
     */
    @Override
    public void shutdown() {
        for (final ScheduledFuture<?> scheduledFuture : this.scheduledFutureArrayList) {
            scheduledFuture.cancel(false);
        }

        this.scheduledFutureArrayList.clear();

        if (this.executorService != null) {
            this.executorService.shutdown();
        }
    }

    /**
     * Returns an unmodifiable view of the active scheduled futures.
     *
     * @return the list of scheduled futures
     */
    @Override
    public List<ScheduledFuture<?>> getScheduledFutureList() {
        return Collections.unmodifiableList(this.scheduledFutureArrayList);
    }

    /**
     * Schedules a single method based on its {@link Scheduler} annotation.
     *
     * <p>In clock-aligned mode, the initial delay is calculated as the
     * time remaining until the next boundary that is a multiple of the
     * interval since the epoch. For example, if the interval is 5 minutes
     * and the current time is 12:07:23, the first execution occurs at
     * 12:10:00, then repeats every 5 minutes.</p>
     *
     * <p>In standard mode, the task runs at a fixed rate relative to
     * when registration occurred.</p>
     *
     * @param instance   the component instance
     * @param method     the annotated method
     * @param annotation the scheduler annotation
     */
    private void schedule(final Object instance, final Method method, final Scheduler annotation) {
        final long intervalMillis = annotation.unit().toMillis(annotation.delay());

        if (intervalMillis <= 0) {
            throw new InjectorException("@%s delay must be positive: %s.%s".formatted(Scheduler.class.getSimpleName(), instance.getClass().getName(), method.getName()));
        }

        if (this.executorService == null) {
            this.executorService = Executors.newScheduledThreadPool(0, runnable -> {
                final Thread thread = new Thread(runnable);
                thread.setDaemon(true);
                thread.setName("di-scheduler");
                return thread;
            });
        }

        if (annotation.clock()) {
            scheduleClock(instance, method, intervalMillis);
        } else {
            final Runnable task = () -> {
                try {
                    method.invoke(instance);
                } catch (final Exception e) {
                    throw new InjectorException("Failed to invoke @%s method: %s.%s".formatted(Scheduler.class.getSimpleName(), instance.getClass().getName(), method.getName()), e);
                }
            };

            final ScheduledFuture<?> future = this.executorService.scheduleAtFixedRate(task, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);

            this.scheduledFutureArrayList.add(future);
        }
    }

    /**
     * Schedules a clock-aligned task that recalculates its next delay
     * from wall-clock time after every execution, ensuring each tick
     * snaps to an exact interval boundary regardless of execution
     * duration or JVM scheduling jitter.
     *
     * @param instance       the component instance
     * @param method         the annotated method
     * @param intervalMillis the interval in milliseconds
     */
    private void scheduleClock(final Object instance, final Method method, final long intervalMillis) {
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                try {
                    method.invoke(instance);
                } catch (final Exception e) {
                    throw new InjectorException("Failed to invoke @%s method: %s.%s".formatted(Scheduler.class.getSimpleName(), instance.getClass().getName(), method.getName()), e);
                }

                final long now = System.currentTimeMillis();
                final long remainder = now % intervalMillis;
                final long nextDelay = (remainder == 0) ? intervalMillis : (intervalMillis - remainder);

                scheduledFutureArrayList.add(executorService.schedule(this, nextDelay, TimeUnit.MILLISECONDS));
            }
        };

        final long now = System.currentTimeMillis();
        final long remainder = now % intervalMillis;
        final long initialDelay = (remainder == 0) ? 0 : (intervalMillis - remainder);

        this.scheduledFutureArrayList.add(this.executorService.schedule(tick, initialDelay, TimeUnit.MILLISECONDS));
    }
}