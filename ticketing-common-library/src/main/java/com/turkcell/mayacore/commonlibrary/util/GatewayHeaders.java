package com.turkcell.mayacore.commonlibrary.util;

public final class GatewayHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_ROLES = "X-User-Roles";
    public static final String FORWARDED_FOR = "X-Forwarded-For";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private GatewayHeaders() {
    }
}
