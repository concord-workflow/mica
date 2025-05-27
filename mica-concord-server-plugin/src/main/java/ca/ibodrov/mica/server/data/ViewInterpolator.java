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

import ca.ibodrov.mica.api.model.ViewLike;
import ca.ibodrov.mica.server.data.Validator.SchemaFetcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class ViewInterpolator {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ViewInterpolator(ObjectMapper objectMapper, SchemaFetcher schemaFetcher) {
        this.objectMapper = requireNonNull(objectMapper);
        this.validator = Validator.getDefault(objectMapper, requireNonNull(schemaFetcher));
    }

    public ViewLike interpolate(ViewLike view, JsonNode input) {
        // validate input
        if (input != null && !input.isNull()) {
            var maybeSchema = view.parameters();
            if (maybeSchema.isEmpty()) {
                // nothing to interpolate
                return view;
            }

            var schema = maybeSchema.get();
            var validatedInput = validator.validateObject(schema, input);
            if (!validatedInput.isValid()) {
                throw validatedInput.toException();
            }
        }

        // TODO check for unresolved parameters

        var selector = interpolate(view.selector(), input);
        var data = interpolate(view.data(), input);
        var validation = view.validation().map(v -> interpolate(v, input));
        var caching = view.caching().map(c -> interpolate(c, input));
        return new ViewLike() {
            @Override
            public String name() {
                return view.name();
            }

            @Override
            public Selector selector() {
                return selector;
            }

            @Override
            public Data data() {
                return data;
            }

            @Override
            public Optional<? extends Validation> validation() {
                return validation;
            }

            @Override
            public Optional<JsonNode> parameters() {
                return view.parameters();
            }

            @Override
            public Optional<? extends Caching> caching() {
                return caching;
            }
        };
    }

    private ViewLike.Selector interpolate(ViewLike.Selector selector, JsonNode input) {
        var includes = selector.includes().map(values -> interpolate(values, input));
        var entityKind = interpolate(selector.entityKind(), input);
        var namePatterns = selector.namePatterns().map(values -> interpolate(values, input));
        return new ViewLike.Selector() {

            @Override
            public Optional<List<String>> includes() {
                return includes;
            }

            @Override
            public String entityKind() {
                return entityKind;
            }

            @Override
            public Optional<List<String>> namePatterns() {
                return namePatterns;
            }
        };
    }

    private ViewLike.Data interpolate(ViewLike.Data data, JsonNode input) {
        var jsonPath = interpolate(objectMapper, data.jsonPath(), input);
        var mergeBy = data.mergeBy().map(v -> interpolate(objectMapper, v, input));
        var dropProperties = data.dropProperties()
                .map(properties -> properties.stream().map(v -> interpolate(v, input)).toList());
        var map = data.map().map(v -> interpolate(objectMapper, v, input));
        return new ViewLike.Data() {
            @Override
            public JsonNode jsonPath() {
                return jsonPath;
            }

            @Override
            public Optional<Boolean> flatten() {
                return data.flatten();
            }

            @Override
            public Optional<Boolean> merge() {
                return data.merge();
            }

            @Override
            public Optional<JsonNode> mergeBy() {
                return mergeBy;
            }

            @Override
            public Optional<JsonNode> jsonPatch() {
                return data.jsonPatch();
            }

            @Override
            public Optional<List<String>> dropProperties() {
                return dropProperties;
            }

            @Override
            public Optional<Map<String, JsonNode>> map() {
                return map;
            }
        };
    }

    private ViewLike.Validation interpolate(ViewLike.Validation validation, JsonNode input) {
        var validationAsEntityKind = interpolate(validation.asEntityKind(), input);
        return () -> validationAsEntityKind;
    }

    private ViewLike.Caching interpolate(ViewLike.Caching caching, JsonNode input) {
        var enabled = caching.enabled().map(v -> interpolate(v, input));
        var ttl = caching.ttl().map(v -> interpolate(v, input));
        return new ViewLike.Caching() {
            @Override
            public Optional<String> enabled() {
                return enabled;
            }

            @Override
            public Optional<String> ttl() {
                return ttl;
            }
        };
    }

    private static List<String> interpolate(List<String> strings, JsonNode input) {
        return strings.stream()
                .map(u -> interpolate(u, input))
                .toList();
    }

    private static String interpolate(String s, JsonNode input) {
        if (s == null) {
            return null;
        }

        if (input != null && input.isObject()) {
            // TODO support nested parameters ${parameters.foo.bar[0]}

            for (var fields = input.fields(); fields.hasNext();) {
                var field = fields.next();

                var key = "$parameters." + field.getKey();
                if (!s.contains(key)) {
                    key = "${parameters." + field.getKey() + "}";
                    if (!s.contains(key)) {
                        continue;
                    }
                }

                var value = field.getValue();
                assert value != null;

                s = s.replace(key, value.asText());
            }
        }

        // TODO arrays, etc
        // TODO report unresolved parameters

        return s.replace("${", "\\$\\{");
    }

    private static JsonNode interpolate(ObjectMapper objectMapper, JsonNode s, JsonNode input) {
        if (s.isTextual()) {
            return TextNode.valueOf(interpolate(s.asText(), input));
        } else if (s.isArray()) {
            var l = objectMapper.createArrayNode();
            for (int i = 0; i < s.size(); i++) {
                l.add(interpolate(objectMapper, s.get(i), input));
            }
            return l;
        } else {
            return s;
        }
    }

    private static Map<String, JsonNode> interpolate(ObjectMapper objectMapper,
                                                     Map<String, JsonNode> s,
                                                     JsonNode input) {
        var m = new HashMap<String, JsonNode>(s.size());
        for (var entry : s.entrySet()) {
            m.put(entry.getKey(), interpolate(objectMapper, entry.getValue(), input));
        }
        return m;
    }
}
