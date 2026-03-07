package io.github.trae.di.resolvers.interfaces;

import java.lang.reflect.Type;

public interface IDependencyResolver {

    Object resolve(final Class<?> type);

    Object resolveCollection(final Type genericType, final Class<?> rawType);
}