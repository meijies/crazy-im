package com.meijie.crazy.rpc.exception;

public class ImRuntimeException extends RuntimeException {
    public ImRuntimeException() {
        super();
    }

    public ImRuntimeException(String message) {
        super(message);
    }

    public ImRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public ImRuntimeException(Throwable cause) {
        super(cause);
    }

    protected ImRuntimeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
