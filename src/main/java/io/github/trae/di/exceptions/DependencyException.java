package io.github.trae.di.exceptions;

/**
 * Thrown when a dependency cannot be resolved, a circular dependency is
 * detected, or a component fails to be constructed.
 */
public class DependencyException extends RuntimeException {

    /**
     * Creates an exception with no message or cause.
     */
    public DependencyException() {
        super();
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public DependencyException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception with the given message.
     *
     * @param message the detail message
     */
    public DependencyException(final String message) {
        super(message);
    }

    /**
     * Creates an exception with the given cause.
     *
     * @param cause the underlying cause
     */
    public DependencyException(final Throwable cause) {
        super(cause);
    }
}