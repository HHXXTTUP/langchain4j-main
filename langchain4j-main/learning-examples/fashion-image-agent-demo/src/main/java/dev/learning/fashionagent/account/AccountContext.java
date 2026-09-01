package dev.learning.fashionagent.account;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AccountContext {
    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    private AccountContext() {}

    public static void set(Snapshot snapshot) { CURRENT.set(snapshot); }
    public static Snapshot current() { return CURRENT.get(); }
    public static Snapshot capture() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }

    public static String value(String key, String fallback) {
        Snapshot snapshot = CURRENT.get();
        String value = snapshot == null ? null : snapshot.settings().get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public static String secretValue(String key, String fallback) {
        Snapshot snapshot = CURRENT.get();
        if (snapshot == null) return fallback;
        String value = snapshot.settings().get(key);
        if (value != null && !value.isBlank()) return value;
        return snapshot.administrator() ? fallback : value;
    }

    public static record Snapshot(String accountId, String username, boolean administrator, Map<String, String> settings) {
        public Snapshot {
            settings = settings == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(settings));
        }
    }
}
