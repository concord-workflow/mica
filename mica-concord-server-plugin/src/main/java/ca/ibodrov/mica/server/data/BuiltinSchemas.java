package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.kinds.MicaDashboardV1;
import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.ViewLike;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static ca.ibodrov.mica.api.kinds.MicaDashboardV1.MICA_DASHBOARD_V1;
import static ca.ibodrov.mica.api.kinds.MicaViewV1.MICA_VIEW_V1;
import static java.util.function.Function.identity;

public final class BuiltinSchemas {

    public static final String STANDARD_PROPERTIES_V1 = "/mica/standard-properties/v1";
    public static final String INTERNAL_ENTITY_STORE_URI = "mica://internal";
    public static final String STANDARD_PROPERTIES_REF = INTERNAL_ENTITY_STORE_URI + STANDARD_PROPERTIES_V1;
    public static final String EXTERNAL_JSON_SCHEMA_REF = "https://json-schema.org/draft/2020-12/schema";
    public static final String JSON_SCHEMA_REF = "classpath:///draft/2020-12/schema";

    private static final TypeReference<List<String>> LIST_OF_STRINGS = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, JsonNode>> MAP_OF_JSON_NODES = new TypeReference<>() {
    };

    public static MicaDashboardV1 asMicaDashboardV1(EntityLike entity) {
        assertKind(entity, MICA_DASHBOARD_V1);

        var title = select(entity, "title", JsonNode::asText)
                .orElseThrow(() -> ApiException.badRequest("Dashboard is missing title"));

        var view = asViewRef(entity);

        var layout = select(entity, "layout", JsonNode::asText)
                .map(BuiltinSchemas::parseLayout)
                .orElseThrow(() -> ApiException.badRequest("Dashboard is missing layout"));

        var table = asTableLayout(entity);

        return new MicaDashboardV1(title, view, layout, table);
    }

    public static MicaDashboardV1.ViewRef asViewRef(EntityLike entity) {

        var name = select(entity, "view", "name", JsonNode::asText)
                .orElseThrow(() -> ApiException.badRequest("view is missing name"));

        var parameters = select(entity, "view", "parameters", identity());

        return new MicaDashboardV1.ViewRef(name, parameters);
    }

    public static Optional<MicaDashboardV1.TableLayout> asTableLayout(EntityLike entity) {
        return select(entity, "table", "columns", n -> {
            var list = ImmutableList.<MicaDashboardV1.TableColumnDef>builder();
            n.elements().forEachRemaining(col -> {
                var title = assertString(col, "table.columns[*]", "title");
                var jsonPath = assertString(col, "table.columns[*]", "jsonPath");
                list.add(new MicaDashboardV1.TableColumnDef(title, jsonPath));
            });
            return new MicaDashboardV1.TableLayout(list.build());
        });
    }

    public static ViewLike asViewLike(ObjectMapper objectMapper, EntityLike entity) {
        assertKind(entity, MICA_VIEW_V1);

        var name = entity.name();
        var parameters = Optional.ofNullable(entity.data().get("parameters")).filter(n -> !n.isNull());
        var selector = asViewLikeSelector(objectMapper, entity);
        var data = asViewLikeData(objectMapper, entity);
        var validation = asViewLikeValidation(entity);
        var caching = asViewLikeCaching(entity);

        return new ViewLike() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Optional<JsonNode> parameters() {
                return parameters;
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
            public Optional<? extends Caching> caching() {
                return caching;
            }
        };
    }

    private static ViewLike.Selector asViewLikeSelector(ObjectMapper objectMapper, EntityLike entity) {
        // TODO better validation, propagate convertValue errors
        var includes = select(entity, "selector", "includes", n -> objectMapper.convertValue(n, LIST_OF_STRINGS));

        var entityKind = select(entity, "selector", "entityKind", JsonNode::asText)
                .orElseThrow(() -> ApiException.badRequest("View is missing selector.entityKind"));

        var namePatterns = select(entity, "selector", "namePatterns",
                n -> objectMapper.convertValue(n, LIST_OF_STRINGS));

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

    private static ViewLike.Data asViewLikeData(ObjectMapper objectMapper, EntityLike entity) {
        var jsonPath = select(entity, "data", "jsonPath", identity())
                .orElseThrow(() -> ApiException.badRequest("View is missing data.jsonPath"));

        var flatten = select(entity, "data", "flatten", JsonNode::asBoolean);

        var merge = select(entity, "data", "merge", JsonNode::asBoolean);

        var mergeBy = select(entity, "data", "mergeBy", identity());

        var jsonPatch = select(entity, "data", "jsonPatch", identity());

        var dropProperties = select(entity, "data", "dropProperties",
                n -> objectMapper.convertValue(n, LIST_OF_STRINGS));

        var map = select(entity, "data", "map",
                n -> objectMapper.convertValue(n, MAP_OF_JSON_NODES));

        return new ViewLike.Data() {
            @Override
            public JsonNode jsonPath() {
                return jsonPath;
            }

            @Override
            public Optional<Boolean> flatten() {
                return flatten;
            }

            @Override
            public Optional<Boolean> merge() {
                return merge;
            }

            @Override
            public Optional<JsonNode> mergeBy() {
                return mergeBy;
            }

            @Override
            public Optional<JsonNode> jsonPatch() {
                return jsonPatch;
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

    private static Optional<ViewLike.Validation> asViewLikeValidation(EntityLike entity) {
        var entityKind = select(entity, "validation", "asEntityKind", JsonNode::asText);
        return entityKind.map(v -> () -> v);
    }

    private static Optional<ViewLike.Caching> asViewLikeCaching(EntityLike entity) {
        var enabled = select(entity, "caching", "enabled", JsonNode::asText);
        var ttl = select(entity, "caching", "ttl", JsonNode::asText);
        return Optional.of(new ViewLike.Caching() {
            @Override
            public Optional<String> enabled() {
                return enabled;
            }

            @Override
            public Optional<String> ttl() {
                return ttl;
            }
        });
    }

    private static <T> Optional<T> select(EntityLike entityLike,
                                          String pathElement1,
                                          Function<JsonNode, T> converter) {
        // TODO validate target type
        return Optional.ofNullable(entityLike.data().get(pathElement1))
                .map(converter);
    }

    private static <T> Optional<T> select(EntityLike entityLike,
                                          String pathElement1,
                                          String pathElement2,
                                          Function<JsonNode, T> converter) {
        // TODO validate target type
        return Optional.ofNullable(entityLike.data().get(pathElement1))
                .map(n -> n.get(pathElement2))
                .map(converter);
    }

    private static void assertKind(EntityLike entity, String expectedKind) {
        if (!expectedKind.equals(entity.kind())) {
            throw ApiException.badRequest("Expected a %s entity, got something else. Entity '%s' is a %s"
                    .formatted(expectedKind, entity.name(), entity.kind()));
        }
    }

    private static MicaDashboardV1.Layout parseLayout(String s) {
        try {
            return MicaDashboardV1.Layout.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid dashboard layout type: %s".formatted(s));
        }
    }

    private static String assertString(JsonNode n, String path, String k) {
        var v = n.get(k);
        if (v == null || !v.isTextual()) {
            throw ApiException
                    .badRequest("Expected '%s.%s' to be a string value, got: %s".formatted(path, k, v));
        }
        return v.asText();
    }
}
