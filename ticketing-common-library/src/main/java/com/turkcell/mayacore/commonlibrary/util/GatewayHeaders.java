package com.turkcell.mayacore.commonlibrary.util;

public final class GatewayHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String SESSION_ID = "X-Session-Id";
    public static final String FORWARDED_FOR = "X-Forwarded-For";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    /** Gateway → downstream trust token; clients must not forge this. */
    public static final String GATEWAY_SECRET = "X-Gateway-Secret";

    private GatewayHeaders() {
    }
}
