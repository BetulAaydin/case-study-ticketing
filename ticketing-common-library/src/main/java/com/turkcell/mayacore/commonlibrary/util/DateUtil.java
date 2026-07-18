package com.turkcell.mayacore.commonlibrary.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class DateUtil {

    private DateUtil() {
    }

    public static String toIso8601(LocalDateTime dt) {
        return dt.format(DateTimeFormatter.ISO_DATE_TIME);
    }

    public static LocalDateTime fromIso8601(String s) {
        return LocalDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME);
    }

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
