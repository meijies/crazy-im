package com.meijie.exception;

public class RpcNoSuchMethodException extends Exception {
    public RpcNoSuchMethodException() {
        super();
    }

    public RpcNoSuchMethodException(String message) {
        super(message);
    }

    public RpcNoSuchMethodException(String message, Throwable cause) {
        super(message, cause);
    }

    public RpcNoSuchMethodException(Throwable cause) {
        super(cause);
    }

    protected RpcNoSuchMethodException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
