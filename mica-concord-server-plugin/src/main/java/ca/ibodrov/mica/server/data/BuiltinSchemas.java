package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.ViewLike;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import ca.ibodrov.mica.schema.ValueType;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import javax.inject.Inject;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static ca.ibodrov.mica.api.kinds.MicaKindV1.MICA_KIND_V1;
import static ca.ibodrov.mica.api.kinds.MicaKindV1.SCHEMA_PROPERTY;
import static ca.ibodrov.mica.api.kinds.MicaViewV1.MICA_VIEW_V1;
import static ca.ibodrov.mica.schema.ObjectSchemaNode.*;
import static ca.ibodrov.mica.schema.ValueType.OBJECT;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT;

public final class BuiltinSchemas {

    private static final TypeReference<List<String>> LIST_OF_STRINGS = new TypeReference<>() {
    };

    private static final TypeReference<List<URI>> LIST_OF_URIS = new TypeReference<>() {
    };

    public static final String MICA_OBJECT_SCHEMA_NODE_V1 = "/mica/objectSchemaNode/v1";
    public static final String MICA_RECORD_V1 = "/mica/record/v1";

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
                SCHEMA_PROPERTY, objectSchemaNodeSchema),
                Set.of("kind", "name", SCHEMA_PROPERTY));

        var viewSelector = object(Map.of("entityKind", string()), Set.of("entityKind"));
        var viewData = object(Map.of(
                "jsonPath", string(),
                "jsonPatch", array(object()),
                "flatten", bool(),
                "merge", bool()),
                Set.of("jsonPath"));
        var viewValidation = object(Map.of("asEntityKind", string()), Set.of("asEntityKind"));

        this.micaViewV1Schema = object(Map.of(
                "id", string(),
                "kind", enums(TextNode.valueOf(MICA_VIEW_V1)),
                "name", string(),
                "parameters", objectSchemaNodeSchema,
                "selector", viewSelector,
                "data", viewData,
                "validation", viewValidation),
                Set.of("kind", "name", "selector", "data"));
        // TODO disallow other properties
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
                .filter(n -> !n.isNull())
                .map(object -> parseParameters(objectMapper, object));

        var selector = asViewLikeSelector(objectMapper, entity);

        var data = asViewLikeData(objectMapper, entity);

        var validation = asViewLikeValidation(entity);

        return new ViewLike() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Optional<ObjectSchemaNode> parameters() {
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
        };
    }

    private static ViewLike.Selector asViewLikeSelector(ObjectMapper objectMapper, EntityLike entity) {
        // TODO better validation, propagate convertValue errors
        var includes = select(entity, "data", "includes", n -> objectMapper.convertValue(n, LIST_OF_URIS));

        var entityKind = select(entity, "selector", "entityKind", JsonNode::asText)
                .orElseThrow(() -> ApiException.badRequest("View is missing selector.entityKind"));

        var namePatterns = select(entity, "selector", "namePatterns",
                n -> objectMapper.convertValue(n, LIST_OF_STRINGS));

        return new ViewLike.Selector() {

            @Override
            public Optional<List<URI>> includes() {
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
        var jsonPath = select(entity, "data", "jsonPath", JsonNode::asText)
                .orElseThrow(() -> ApiException.badRequest("View is missing data.jsonPath"));

        var flatten = select(entity, "data", "flatten", JsonNode::asBoolean);

        var merge = select(entity, "data", "merge", JsonNode::asBoolean);

        var jsonPatch = select(entity, "data", "jsonPatch", Function.identity());

        return new ViewLike.Data() {
            @Override
            public String jsonPath() {
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
            public Optional<JsonNode> jsonPatch() {
                return jsonPatch;
            }
        };
    }

    private static Optional<ViewLike.Validation> asViewLikeValidation(EntityLike entity) {
        var entityKind = select(entity, "validation", "asEntityKind", JsonNode::asText);
        return entityKind.map(v -> () -> v);
    }

    private static ObjectSchemaNode parseParameters(ObjectMapper objectMapper, JsonNode parameters) {
        var strictMapper = objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        try {
            return strictMapper.convertValue(parameters, ObjectSchemaNode.class);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Error while parsing 'parameters': " + e.getMessage());
        }
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
