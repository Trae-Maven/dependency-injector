package io.github.trae.di.exceptions;

public class InjectorException extends RuntimeException {

    public InjectorException() {
        super();
    }

    public InjectorException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public InjectorException(final String message) {
        super(message);
    }

    public InjectorException(final Throwable cause) {
        super(cause);
    }
}