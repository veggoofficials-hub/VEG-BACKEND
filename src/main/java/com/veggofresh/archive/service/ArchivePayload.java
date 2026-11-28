package com.veggofresh.archive.service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny helper to build the JSON payload of an archive item. Null values are skipped. */
public final class ArchivePayload {

    private ArchivePayload() {
    }

    /**
     * {@code of("bankName", x, "ifscCode", y)}. Numbers, booleans and strings are kept as they are;
     * anything else (enums, ids, dates) is stored as its text form so the JSON stays simple.
     */
    public static Map<String, Object> of(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            if (value == null) {
                continue;
            }
            if (value instanceof Number || value instanceof Boolean || value instanceof String) {
                map.put(String.valueOf(keyValues[i]), value);
            } else {
                map.put(String.valueOf(keyValues[i]), value.toString());
            }
        }
        return map;
    }

    /** True when at least one of the values is non-null and not blank. */
    public static boolean hasAny(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return true;
            }
        }
        return false;
    }
}
