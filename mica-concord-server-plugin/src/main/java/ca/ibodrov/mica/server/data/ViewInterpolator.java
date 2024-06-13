package ca.ibodrov.mica.server.data;

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

        var selectorIncludes = view.selector().includes().map(includes -> interpolate(includes, input));
        var selectorEntityKind = interpolate(view.selector().entityKind(), input);
        var selectorNamePatterns = view.selector().namePatterns().map(namePatterns -> interpolate(namePatterns, input));
        var dataJsonPath = interpolate(objectMapper, view.data().jsonPath(), input);
        var dataMergeBy = view.data().mergeBy().map(v -> interpolate(objectMapper, v, input));
        var dropProperties = view.data().dropProperties()
                .map(properties -> properties.stream().map(v -> interpolate(v, input)).toList());
        var dataMap = view.data().map().map(map -> interpolate(objectMapper, map, input));
        var validationAsEntityKind = view.validation()
                .flatMap(v -> Optional.ofNullable(interpolate(v.asEntityKind(), input)));
        var cachingEnabled = view.caching().flatMap(c -> c.enabled().map(v -> interpolate(v, input)));
        var cachingTtl = view.caching().flatMap(c -> c.ttl().map(v -> interpolate(v, input)));

        return new ViewLike() {
            @Override
            public String name() {
                return view.name();
            }

            @Override
            public Selector selector() {
                return new Selector() {
                    @Override
                    public Optional<List<String>> includes() {
                        return selectorIncludes;
                    }

                    @Override
                    public String entityKind() {
                        return selectorEntityKind;
                    }

                    @Override
                    public Optional<List<String>> namePatterns() {
                        return selectorNamePatterns;
                    }
                };
            }

            @Override
            public Data data() {
                return new Data() {
                    @Override
                    public JsonNode jsonPath() {
                        return dataJsonPath;
                    }

                    @Override
                    public Optional<Boolean> flatten() {
                        return view.data().flatten();
                    }

                    @Override
                    public Optional<Boolean> merge() {
                        return view.data().merge();
                    }

                    @Override
                    public Optional<JsonNode> mergeBy() {
                        return dataMergeBy;
                    }

                    @Override
                    public Optional<JsonNode> jsonPatch() {
                        return view.data().jsonPatch();
                    }

                    @Override
                    public Optional<List<String>> dropProperties() {
                        return dropProperties;
                    }

                    @Override
                    public Optional<Map<String, JsonNode>> map() {
                        return dataMap;
                    }
                };
            }

            @Override
            public Optional<? extends Validation> validation() {
                return validationAsEntityKind.map(entityKind -> () -> entityKind);
            }

            @Override
            public Optional<JsonNode> parameters() {
                return view.parameters();
            }

            @Override
            public Optional<? extends Caching> caching() {
                return Optional.of(new Caching() {
                    @Override
                    public Optional<String> enabled() {
                        return cachingEnabled;
                    }

                    @Override
                    public Optional<String> ttl() {
                        return cachingTtl;
                    }
                });
            }
        };
    }

    private static List<String> interpolate(List<String> uris, JsonNode input) {
        return uris.stream()
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
