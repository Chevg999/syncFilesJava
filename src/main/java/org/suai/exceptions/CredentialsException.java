package org.suai.exceptions;

public class CredentialsException extends ClientException {
    public CredentialsException(String message) {
        super(message);
    }

    public CredentialsException(String message, Throwable cause) {
        super(message, cause);
    }

    public CredentialsException(Throwable cause) {
        super(cause);
    }

    public CredentialsException() {
        super();
    }
}
