package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.ViewLike;
import ca.ibodrov.mica.server.data.Validator.SchemaFetcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

public class ViewInterpolator {

    private final Validator validator;

    public ViewInterpolator(ObjectMapper objectMapper, SchemaFetcher schemaFetcher) {
        this.validator = Validator.getDefault(objectMapper, schemaFetcher);
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
        var dataJsonPath = interpolate(view.data().jsonPath(), input);
        var validationAsEntityKind = view.validation()
                .flatMap(v -> Optional.ofNullable(interpolate(v.asEntityKind(), input)));

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
                    public String jsonPath() {
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
                    public Optional<JsonNode> jsonPatch() {
                        return view.data().jsonPatch();
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

                if (value.isTextual() || value.isNumber() || value.isBoolean()) {
                    s = s.replace(key, value.asText());
                } else {
                    s = s.replace(key, value.asText("unknown"));
                }
            }
        }

        // TODO arrays, etc
        // TODO report unresolved parameters

        return s.replace("${", "\\$\\{");
    }
}
