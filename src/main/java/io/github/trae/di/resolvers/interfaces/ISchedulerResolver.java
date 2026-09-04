package io.github.trae.di.resolvers.interfaces;

import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * Discovers scheduled methods on component instances and manages the
 * resulting repeating tasks.
 */
public interface ISchedulerResolver {

    /**
     * Scans the given instance for scheduled methods and registers each one.
     *
     * @param instance the component instance to scan
     */
    void register(final Object instance);

    /**
     * Cancels every task registered by this resolver.
     */
    void shutdown();

    /**
     * Returns an unmodifiable view of the active scheduled futures.
     *
     * @return the list of scheduled futures
     */
    List<ScheduledFuture<?>> getScheduledFutureList();
}