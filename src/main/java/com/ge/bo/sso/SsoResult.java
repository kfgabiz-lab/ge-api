package com.ge.bo.sso;

public record SsoResult(
        boolean success,
        SsoResultCode result,
        String userId,
        String userName,
        String deptCode,
        String deptName,
        String message
) {}
