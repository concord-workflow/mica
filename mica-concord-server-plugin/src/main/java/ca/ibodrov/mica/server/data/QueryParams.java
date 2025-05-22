package ca.ibodrov.mica.server.data;

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

import java.net.URLDecoder;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.*;

public class QueryParams {

    private final Map<String, List<String>> params;

    public QueryParams(String s) {
        this.params = parse(s);
    }

    public Optional<String> getFirst(String key) {
        return Optional.ofNullable(params.get(key))
                .flatMap(r -> Optional.ofNullable(r.get(0)));
    }

    private static Map<String, List<String>> parse(String s) {
        if (s == null || s.isBlank()) {
            return Map.of();
        }
        return Arrays.stream(s.split("&"))
                .map(QueryParams::split)
                .collect(groupingBy(Map.Entry::getKey, HashMap::new, mapping(Map.Entry::getValue, toList())));
    }

    private static AbstractMap.SimpleEntry<String, String> split(String it) {
        var idx = it.indexOf("=");
        var key = idx > 0 ? it.substring(0, idx) : it;
        var value = idx > 0 && it.length() > idx + 1 ? it.substring(idx + 1) : null;
        return new AbstractMap.SimpleEntry<>(
                URLDecoder.decode(key, UTF_8),
                value != null ? URLDecoder.decode(value, UTF_8) : null);
    }
}
