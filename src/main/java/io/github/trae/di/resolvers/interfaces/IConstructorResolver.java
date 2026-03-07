package io.github.trae.di.resolvers.interfaces;

public interface IConstructorResolver {

    Object create(final Class<?> type);
}