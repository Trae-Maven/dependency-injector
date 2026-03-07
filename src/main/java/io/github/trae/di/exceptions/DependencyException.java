package io.github.trae.di.exceptions;

public class DependencyException extends RuntimeException {

    public DependencyException() {
        super();
    }

    public DependencyException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public DependencyException(final String message) {
        super(message);
    }

    public DependencyException(final Throwable cause) {
        super(cause);
    }
}