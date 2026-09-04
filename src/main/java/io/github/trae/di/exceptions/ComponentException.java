package io.github.trae.di.exceptions;

/**
 * Thrown when a component cannot be registered, resolved, or looked up
 * in the container.
 */
public class ComponentException extends RuntimeException {

    /**
     * Creates an exception with no message or cause.
     */
    public ComponentException() {
        super();
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public ComponentException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception with the given message.
     *
     * @param message the detail message
     */
    public ComponentException(final String message) {
        super(message);
    }

    /**
     * Creates an exception with the given cause.
     *
     * @param cause the underlying cause
     */
    public ComponentException(final Throwable cause) {
        super(cause);
    }
}