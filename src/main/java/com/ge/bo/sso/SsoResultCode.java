package com.ge.bo.sso;

public enum SsoResultCode {
    OK, FAIL, ERROR;

    public static SsoResultCode from(String value) {
        if (value == null) return ERROR;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ERROR;
        }
    }
}
