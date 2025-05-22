package ca.ibodrov.mica.concord.task;

/*-
 * ~~~~~~
 * Mica
 * ------
 * Copyright (C) 2023 - 2025 Mica Authors
 * ------
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ======
 */

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
