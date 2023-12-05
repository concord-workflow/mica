package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.ViewLike;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import ca.ibodrov.mica.schema.ValueType;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static ca.ibodrov.mica.schema.ObjectSchemaNode.*;
import static ca.ibodrov.mica.server.exceptions.ApiException.ErrorKind.BAD_DATA;

public final class BuiltinSchemas {

    /**
     * Schema of {@link ObjectSchemaNode} itself.
     */
    private static final ObjectSchemaNode OBJECT_SCHEMA_NODE_SCHEMA = object(Map.of(
            "type", enums(ValueType.valuesAsJson()),
            "properties", object(Map.of(), Set.of()),
            "required", array(string()),
            "enum", array(any()),
            "items", any()), Set.of());

    /**
     * /mica/record/v1 - use to declare entities of any kind.
     */
    public static final String MICA_RECORD_V1 = "/mica/record/v1";
    public static final ObjectSchemaNode MICA_RECORD_V1_SCHEMA = object(Map.of(
            "id", string(),
            "kind", enums(TextNode.valueOf(MICA_RECORD_V1)),
            "name", string(),
            "data", any()),
            Set.of("kind", "name", "data"));

    /**
     * /mica/kind/v1 - use to declare new entity kinds.
     */
    public static final String MICA_KIND_V1 = "/mica/kind/v1";
    public static final String MICA_KIND_SCHEMA_PROPERTY = "schema";
    public static final ObjectSchemaNode MICA_KIND_V1_SCHEMA = object(Map.of(
            "id", string(),
            "kind", enums(TextNode.valueOf(MICA_KIND_V1)),
            "name", string(),
            MICA_KIND_SCHEMA_PROPERTY, OBJECT_SCHEMA_NODE_SCHEMA),
            Set.of("kind", "name", "schema"));

    /**
     * /mica/view/v1 - use to declare entity views.
     */
    public static final String MICA_VIEW_V1 = "/mica/view/v1";
    public static final ObjectSchemaNode MICA_VIEW_V1_SCHEMA = object(Map.of(
            "id", string(),
            "kind", enums(TextNode.valueOf(MICA_VIEW_V1)),
            "name", string(),
            "parameters", object(Map.of(), Set.of()),
            "selector", object(Map.of("entityKind", string()), Set.of("entityKind")),
            "data", object(Map.of(
                    "jsonPath", string(),
                    "flatten", bool(),
                    "merge", bool()),
                    Set.of("jsonPath"))),
            Set.of("kind", "name", "selector", "data"));

    public static ViewLike asView(ObjectMapper objectMapper, EntityLike entity) {
        if (!entity.kind().equals(BuiltinSchemas.MICA_VIEW_V1)) {
            throw ApiException.badRequest(BAD_DATA, "Expected a /mica/view/v1 entity, got: " + entity.kind());
        }

        var name = entity.name();

        var parameters = Optional.ofNullable(entity.data().get("parameters"))
                .map(object -> parseParameters(objectMapper, object))
                .orElseGet(Map::of);

        var selectorEntityKind = select(entity, "selector", "entityKind", JsonNode::asText)
                .orElseThrow(() -> ApiException.badRequest(BAD_DATA, "View is missing selector.entityKind"));

        var dataJsonPath = select(entity, "data", "jsonPath", JsonNode::asText)
                .orElseThrow(() -> ApiException.badRequest(BAD_DATA, "View is missing data.jsonPath"));

        var flatten = select(entity, "data", "flatten", JsonNode::asBoolean)
                .orElse(false);

        var merge = select(entity, "data", "merge", JsonNode::asBoolean)
                .orElse(false);

        return new ViewLike() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Map<String, ObjectSchemaNode> parameters() {
                return parameters;
            }

            @Override
            public Selector selector() {
                return () -> selectorEntityKind;
            }

            @Override
            public Data data() {
                return new Data() {
                    @Override
                    public String jsonPath() {
                        return dataJsonPath;
                    }

                    @Override
                    public boolean flatten() {
                        return flatten;
                    }

                    @Override
                    public boolean merge() {
                        return merge;
                    }
                };
            }
        };
    }

    private static Map<String, ObjectSchemaNode> parseParameters(ObjectMapper objectMapper, JsonNode parameters) {
        var result = new HashMap<String, ObjectSchemaNode>();
        parameters.fields().forEachRemaining(field -> {
            var name = field.getKey();
            var value = field.getValue();
            var schema = objectMapper.convertValue(value, ObjectSchemaNode.class);
            if (schema == null) {
                throw ApiException.badRequest(BAD_DATA,
                        "Expected a parameter definition for '%s', got: %s".formatted(name, value));
            }
            result.put(name, schema);
        });
        return result;
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

    private BuiltinSchemas() {
    }
}
