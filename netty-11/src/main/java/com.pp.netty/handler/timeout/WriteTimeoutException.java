package com.pp.netty.handler.timeout;

public final class WriteTimeoutException extends TimeoutException {

    private static final long serialVersionUID = -144786655770296065L;

    public static final WriteTimeoutException INSTANCE = new WriteTimeoutException(true);

    private WriteTimeoutException() { }

    private WriteTimeoutException(boolean shared) {
        super(shared);
    }
}
