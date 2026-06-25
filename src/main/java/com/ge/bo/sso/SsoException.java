package com.ge.bo.sso;

public class SsoException extends RuntimeException {
    public SsoException(String message) { super(message); }
    public SsoException(String message, Throwable cause) { super(message, cause); }
}
