package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.ViewLike;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import ca.ibodrov.mica.schema.ValueType;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static ca.ibodrov.mica.api.kinds.MicaViewV1.MICA_VIEW_V1;
import static ca.ibodrov.mica.schema.ObjectSchemaNode.*;
import static ca.ibodrov.mica.schema.ValueType.OBJECT;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT;

public final class BuiltinSchemas {

    public static final String MICA_OBJECT_SCHEMA_NODE_V1 = "/mica/objectSchemaNode/v1";
    public static final String MICA_RECORD_V1 = "/mica/record/v1";
    public static final String MICA_KIND_V1 = "/mica/kind/v1";

    public static final String MICA_KIND_SCHEMA_PROPERTY = "schema";

    private final ObjectSchemaNode objectSchemaNodeSchema;
    private final ObjectSchemaNode micaRecordV1Schema;
    private final ObjectSchemaNode micaKindV1Schema;
    private final ObjectSchemaNode micaViewV1Schema;

    @Inject
    public BuiltinSchemas(ObjectMapper objectMapper) {
        var mapper = objectMapper.setDefaultPropertyInclusion(NON_ABSENT);

        this.objectSchemaNodeSchema = object(Map.of(
                "type", enums(ValueType.valuesAsJson()),
                "properties", new Builder()
                        .type(OBJECT)
                        .additionalProperties(mapper.convertValue(ref(MICA_OBJECT_SCHEMA_NODE_V1), JsonNode.class))
                        .build(),
                "required", array(string()),
                "enum", array(any()),
                "items", any()),
                Set.of());

        this.micaRecordV1Schema = object(Map.of(
                "id", string(),
                "kind", enums(TextNode.valueOf(MICA_RECORD_V1)),
                "name", string(),
                "data", any()),
                Set.of("kind", "name", "data"));

        this.micaKindV1Schema = object(Map.of(
                "id", string(),
                "kind", enums(TextNode.valueOf(MICA_KIND_V1)),
                "name", string(),
                MICA_KIND_SCHEMA_PROPERTY, objectSchemaNodeSchema),
                Set.of("kind", "name", MICA_KIND_SCHEMA_PROPERTY));

        var viewSelector = object(Map.of("entityKind", string()), Set.of("entityKind"));
        var viewData = object(Map.of(
                "jsonPath", string(),
                "jsonPatch", array(object()),
                "flatten", bool(),
                "merge", bool()),
                Set.of("jsonPath"));

        this.micaViewV1Schema = object(Map.of(
                "id", string(),
                "kind", enums(TextNode.valueOf(MICA_VIEW_V1)),
                "name", string(),
                "parameters", object(),
                "selector", viewSelector,
                "data", viewData),
                Set.of("kind", "name", "selector", "data"));
    }

    /**
     * Schema of {@link ObjectSchemaNode} itself.
     */
    public ObjectSchemaNode getObjectSchemaNodeSchema() {
        return objectSchemaNodeSchema;
    }

    /**
     * /mica/record/v1 - use to declare entities of any kind.
     */
    public ObjectSchemaNode getMicaRecordV1Schema() {
        return micaRecordV1Schema;
    }

    /**
     * /mica/kind/v1 - use to declare new entity kinds.
     */
    public ObjectSchemaNode getMicaKindV1Schema() {
        return micaKindV1Schema;
    }

    /**
     * /mica/view/v1 - use to declare entity views.
     */
    public ObjectSchemaNode getMicaViewV1Schema() {
        return micaViewV1Schema;
    }

    public Optional<ObjectSchemaNode> get(String kind) {
        return switch (kind) {
            case MICA_OBJECT_SCHEMA_NODE_V1 -> Optional.of(objectSchemaNodeSchema);
            case MICA_RECORD_V1 -> Optional.of(micaRecordV1Schema);
            case MICA_KIND_V1 -> Optional.of(micaKindV1Schema);
            case MICA_VIEW_V1 -> Optional.of(micaViewV1Schema);
            default -> Optional.empty();
        };
    }

    public static ViewLike asViewLike(ObjectMapper objectMapper, EntityLike entity) {
        if (!entity.kind().equals(MICA_VIEW_V1)) {
            throw ApiException.badRequest("Expected a %s entity, got: %s".formatted(MICA_VIEW_V1, entity.kind()));
        }

        var name = entity.name();

        var parameters = Optional.ofNullable(entity.data().get("parameters"))
                .map(object -> parseParameters(objectMapper, object));

        var selector = asViewLikeSelector(entity);

        var data = asViewLikeData(entity);

        return new ViewLike() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Optional<Map<String, ObjectSchemaNode>> parameters() {
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
        };
    }

    private static ViewLike.Selector asViewLikeSelector(EntityLike entity) {
        var selectorEntityKind = select(entity, "selector", "entityKind", JsonNode::asText)
                .orElseThrow(() -> ApiException.badRequest("View is missing selector.entityKind"));
        return () -> selectorEntityKind;
    }

    private static ViewLike.Data asViewLikeData(EntityLike entity) {
        var dataJsonPath = select(entity, "data", "jsonPath", JsonNode::asText)
                .orElseThrow(() -> ApiException.badRequest("View is missing data.jsonPath"));

        var dataJsonPatch = select(entity, "data", "jsonPatch", Function.identity());

        var flatten = select(entity, "data", "flatten", JsonNode::asBoolean);

        var merge = select(entity, "data", "merge", JsonNode::asBoolean);

        return new ViewLike.Data() {
            @Override
            public String jsonPath() {
                return dataJsonPath;
            }

            @Override
            public Optional<JsonNode> jsonPatch() {
                return dataJsonPatch;
            }

            @Override
            public Optional<Boolean> flatten() {
                return flatten;
            }

            @Override
            public Optional<Boolean> merge() {
                return merge;
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
                throw ApiException
                        .badRequest("Expected a parameter definition for '%s', got: %s".formatted(name, value));
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
}
