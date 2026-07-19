package com.turkcell.mayacore.commonlibrary.util;

public final class RedisKeys {

    private static final String APP_PREFIX = "TICKET:";
    public static final String USER_SESSION_PREFIX = APP_PREFIX + "user-session:";
    public static final String RATE_LIMIT_PREFIX = APP_PREFIX + "rate-limit:";
    public static final String IDEMPOTENCY_PREFIX = APP_PREFIX + "idempotency:";

    private RedisKeys() {
    }

    public static String userSessionKey(String sessionId) {
        return USER_SESSION_PREFIX + sessionId;
    }

    public static String rateLimitKey(String identifier, long windowId) {
        return RATE_LIMIT_PREFIX + identifier + ":" + windowId;
    }

    public static String idempotencyKey(String key, String endpoint) {
        return IDEMPOTENCY_PREFIX + key + ":" + endpoint;
    }
}
