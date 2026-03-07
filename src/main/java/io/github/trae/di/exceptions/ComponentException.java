package io.github.trae.di.exceptions;

public class ComponentException extends RuntimeException {

    public ComponentException() {
        super();
    }

    public ComponentException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public ComponentException(final String message) {
        super(message);
    }

    public ComponentException(final Throwable cause) {
        super(cause);
    }
}