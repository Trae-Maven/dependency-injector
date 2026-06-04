package io.github.trae.di.resolvers.interfaces;

import java.util.List;

public interface IScanResolver {

    List<String> resolve(final Class<?> bootstrapType);
}