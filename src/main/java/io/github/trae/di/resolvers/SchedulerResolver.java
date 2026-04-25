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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Discovers {@link Scheduler @Scheduler}-annotated methods on component
 * instances and registers them with a shared
 * {@link ScheduledExecutorService}.
 *
 * <p>Supports two scheduling modes:</p>
 * <ul>
 *   <li><b>Standard</b> ({@code clock = false}) — the task waits an
 *       optional initial delay, then runs at a fixed rate.</li>
 *   <li><b>Clock-aligned</b> ({@code clock = true}) — executions are
 *       anchored to wall-clock boundaries that are exact multiples of
 *       the configured period (since the epoch). For example, a
 *       5-minute period fires at {@code :00}, {@code :05}, {@code :10},
 *       etc. Each tick is scheduled relative to the ideal boundary,
 *       preventing cumulative drift from execution duration or JVM
 *       scheduling jitter. {@code initialDelay} is ignored.</li>
 * </ul>
 *
 * <p>Task execution can be dispatched through platform-provided
 * executors via {@code synchronousExecutor} and {@code asynchronousExecutor}.
 * When {@code asynchronous = false} (the default), the task is dispatched
 * through the synchronous executor (e.g. the game thread). When
 * {@code asynchronous = true}, it is dispatched through the asynchronous
 * executor (e.g. the platform's asynchronous scheduler). If no executor
 * is set for the requested mode, the task runs directly on the internal
 * scheduler thread pool.</p>
 *
 * <p>All scheduled futures are tracked so they can be cancelled
 * cleanly during {@link #shutdown()}.</p>
 */
public class SchedulerResolver extends AbstractResolver implements ISchedulerResolver {

    private final List<ScheduledFuture<?>> scheduledFutureArrayList = new ArrayList<>();

    /**
     * Optional executor for dispatching tasks with {@code asynchronous = false}
     * onto the platform's main thread. If {@code null}, synchronous
     * tasks run directly on the internal scheduler thread pool.
     */
    private final Consumer<Runnable> synchronousExecutor;

    /**
     * Optional executor for dispatching tasks with {@code asynchronous = true}
     * onto the platform's asynchronous thread pool. If {@code null},
     * asynchronous tasks run directly on the internal scheduler thread pool.
     */
    private final Consumer<Runnable> asynchronousExecutor;

    private ScheduledExecutorService executorService;

    /**
     * Creates a new resolver with optional platform executors. The
     * backing {@link ScheduledExecutorService} is not created until
     * the first {@link Scheduler @Scheduler}-annotated method is
     * discovered, avoiding unnecessary thread allocation when an
     * application has no scheduled tasks.
     *
     * @param componentContainer   the shared component container
     * @param synchronousExecutor  executor for synchronous tasks, or {@code null}
     * @param asynchronousExecutor executor for asynchronous tasks, or {@code null}
     */
    public SchedulerResolver(final ComponentContainer componentContainer, final Consumer<Runnable> synchronousExecutor, final Consumer<Runnable> asynchronousExecutor) {
        super(componentContainer);

        this.synchronousExecutor = synchronousExecutor;
        this.asynchronousExecutor = asynchronousExecutor;
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
     * period since the epoch. {@code initialDelay} is ignored.</p>
     *
     * <p>In standard mode, the task waits {@code initialDelay} (or
     * {@code period} if {@code initialDelay} is 0) then runs at a
     * fixed rate of {@code period}.</p>
     *
     * @param instance   the component instance
     * @param method     the annotated method
     * @param annotation the scheduler annotation
     */
    private void schedule(final Object instance, final Method method, final Scheduler annotation) {
        final long periodMillis = annotation.unit().toMillis(annotation.period());

        if (periodMillis <= 0) {
            throw new InjectorException("@%s period must be positive: %s.%s".formatted(Scheduler.class.getSimpleName(), instance.getClass().getName(), method.getName()));
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
            scheduleClock(instance, method, annotation.asynchronous(), periodMillis);
        } else {
            final long initialDelayMillis = annotation.initialDelay() > 0 ? annotation.unit().toMillis(annotation.initialDelay()) : periodMillis;

            final Runnable task = wrapTask(instance, method, annotation.asynchronous());

            final ScheduledFuture<?> future = this.executorService.scheduleAtFixedRate(task, initialDelayMillis, periodMillis, TimeUnit.MILLISECONDS);

            this.scheduledFutureArrayList.add(future);
        }
    }

    /**
     * Schedules a clock-aligned task anchored to ideal wall-clock
     * boundaries. Each tick is scheduled relative to where it
     * <i>should</i> have fired (not when it actually ran), so
     * execution duration and JVM jitter never accumulate drift.
     *
     * @param instance     the component instance
     * @param method       the annotated method
     * @param asynchronous whether the task should be dispatched through
     *                     the asynchronous executor
     * @param periodMillis the period in milliseconds
     */
    private void scheduleClock(final Object instance, final Method method, final boolean asynchronous, final long periodMillis) {
        final long now = System.currentTimeMillis();
        final long remainder = now % periodMillis;
        final long initialDelay = (remainder == 0) ? 0 : (periodMillis - remainder);
        final AtomicLong expected = new AtomicLong(now + initialDelay);

        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                final long target = expected.get();
                final long diff = target - System.currentTimeMillis();

                if (diff > 0) {
                    try {
                        Thread.sleep(diff);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                wrapTask(instance, method, asynchronous).run();

                final long nextExpected = expected.addAndGet(periodMillis);
                final long nextDelay = Math.max(0, nextExpected - System.currentTimeMillis());

                scheduledFutureArrayList.add(executorService.schedule(this, nextDelay, TimeUnit.MILLISECONDS));
            }
        };

        this.scheduledFutureArrayList.add(this.executorService.schedule(tick, initialDelay, TimeUnit.MILLISECONDS));
    }

    /**
     * Wraps a method invocation with platform executor dispatch.
     * If {@code asynchronous} is {@code true} and an asynchronous
     * executor is set, the invocation is dispatched through it. If
     * {@code asynchronous} is {@code false} and a synchronous executor
     * is set, the invocation is dispatched through that. Otherwise the
     * invocation runs directly on the current thread.
     *
     * @param instance     the component instance
     * @param method       the annotated method
     * @param asynchronous whether to use the asynchronous or synchronous executor
     * @return a runnable that dispatches the method invocation
     */
    private Runnable wrapTask(final Object instance, final Method method, final boolean asynchronous) {
        final Runnable invocation = () -> {
            try {
                method.invoke(instance);
            } catch (final Exception e) {
                throw new InjectorException("Failed to invoke @%s method: %s.%s".formatted(Scheduler.class.getSimpleName(), instance.getClass().getName(), method.getName()), e);
            }
        };

        return () -> {
            if (asynchronous) {
                if (this.asynchronousExecutor != null) {
                    this.asynchronousExecutor.accept(invocation);
                } else {
                    invocation.run();
                }
            } else {
                if (this.synchronousExecutor != null) {
                    this.synchronousExecutor.accept(invocation);
                } else {
                    invocation.run();
                }
            }
        };
    }
}