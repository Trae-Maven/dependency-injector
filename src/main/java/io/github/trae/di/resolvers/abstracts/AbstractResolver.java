package io.github.trae.di.resolvers.abstracts;

import io.github.trae.di.containers.ComponentContainer;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter(AccessLevel.PROTECTED)
public abstract class AbstractResolver {

    private final ComponentContainer componentContainer;
}