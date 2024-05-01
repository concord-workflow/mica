package ca.ibodrov.mica.concord.task;

import com.google.common.annotations.VisibleForTesting;
import com.walmartlabs.concord.runtime.v2.runner.SensitiveDataHolder;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

public final class SensitiveDataUtils {

    @VisibleForTesting
    static final int MAX_DEPTH = 32;

    public static <T> T hideSensitiveData(T value) {
        return hideSensitiveData(value, Set.of());
    }

    @SuppressWarnings("unchecked")
    public static <T> T hideSensitiveData(T value, Set<String> exclusions) {
        var holder = SensitiveDataHolder.getInstance();
        var sensitiveData = holder.get();
        if (sensitiveData.isEmpty()) {
            return value;
        }
        var effectiveSensitiveData = new HashSet<>(sensitiveData);
        effectiveSensitiveData.removeAll(exclusions);
        return (T) hideSensitiveData(effectiveSensitiveData, value, 0);
    }

    private static Object hideSensitiveData(Set<String> sensitiveData, Object value, int depth) {
        if (depth > MAX_DEPTH) {
            return "...to deep";
        }

        if (value instanceof String v) {
            for (String s : sensitiveData) {
                v = v.replace(s, "_*****");
            }
            return v;
        }

        if (value instanceof Map<?, ?> m) {
            return m.entrySet()
                    .stream()
                    .collect(toMap(
                            e -> hideSensitiveData(sensitiveData, e.getKey(), depth + 1),
                            e -> hideSensitiveData(sensitiveData, e.getValue(), depth + 1),
                            (a, b) -> b,
                            LinkedHashMap::new));
        }

        if (value instanceof List<?> l) {
            return l.stream()
                    .map(e -> hideSensitiveData(sensitiveData, e, depth + 1))
                    .collect(Collectors.toList());
        }

        return value;
    }

    private SensitiveDataUtils() {
    }
}
