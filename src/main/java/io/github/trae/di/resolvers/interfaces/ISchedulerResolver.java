package io.github.trae.di.resolvers.interfaces;

import java.util.List;
import java.util.concurrent.ScheduledFuture;

public interface ISchedulerResolver {

    void register(final Object instance);

    void shutdown();

    List<ScheduledFuture<?>> getScheduledFutureList();
}