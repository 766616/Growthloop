package com.growthloop.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** Null-friendly ordered map builder for JSON responses and AI payloads. */
public final class M {
    private M() {}

    public static Map<String, Object> of(Object... kv) {
        if (kv.length % 2 != 0) throw new IllegalArgumentException("Odd number of key/value arguments");
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
