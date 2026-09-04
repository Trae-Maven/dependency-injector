package io.github.trae.di.exceptions;

/**
 * Thrown when application bootstrapping, scanning, configuration, or
 * lifecycle invocation fails.
 */
public class InjectorException extends RuntimeException {

    /**
     * Creates an exception with no message or cause.
     */
    public InjectorException() {
        super();
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public InjectorException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception with the given message.
     *
     * @param message the detail message
     */
    public InjectorException(final String message) {
        super(message);
    }

    /**
     * Creates an exception with the given cause.
     *
     * @param cause the underlying cause
     */
    public InjectorException(final Throwable cause) {
        super(cause);
    }
}