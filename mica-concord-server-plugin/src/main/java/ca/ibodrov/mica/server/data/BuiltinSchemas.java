package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.ViewLike;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.intellij.lang.annotations.Language;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static ca.ibodrov.mica.api.kinds.MicaKindV1.MICA_KIND_V1;
import static ca.ibodrov.mica.api.kinds.MicaViewV1.MICA_VIEW_V1;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT;

public final class BuiltinSchemas {

    public static final String STANDARD_PROPERTIES_V1 = "/mica/standard-properties/v1";
    public static final String INTERNAL_ENTITY_STORE_URI = "mica://internal";
    public static final String STANDARD_PROPERTIES_REF = INTERNAL_ENTITY_STORE_URI + STANDARD_PROPERTIES_V1;
    public static final String EXTERNAL_JSON_SCHEMA_REF = "https://json-schema.org/draft/2020-12/schema";
    public static final String JSON_SCHEMA_REF = "classpath:///draft/2020-12/schema";

    private static final TypeReference<List<String>> LIST_OF_STRINGS = new TypeReference<>() {
    };

    public static final String MICA_RECORD_V1 = "/mica/record/v1";

    private final ObjectNode standardProperties;
    private final ObjectNode micaRecordV1Schema;
    private final ObjectNode micaKindV1Schema;
    private final ObjectNode micaViewV1Schema;

    @Inject
    public BuiltinSchemas(ObjectMapper objectMapper) {
        var mapper = objectMapper.setDefaultPropertyInclusion(NON_ABSENT)
                .copyWith(new YAMLFactory());

        this.standardProperties = makeJsonSchema(mapper, """
                type: object
                properties:
                  id:
                    type: string
                  kind:
                    type: string
                  name:
                    type: string
                  createdAt:
                    type: string
                  updatedAt:
                    type: string
                required: ["kind", "name"]
                """);

        this.micaRecordV1Schema = makeJsonSchema(mapper, """
                $ref: %%STANDARD_PROPERTIES_REF%%
                properties:
                  kind:
                    type: string
                    enum: ["/mica/record/v1"]
                  data:
                    type: [object, array, string, number, boolean, null]
                required: ["kind", "data"]
                """);

        this.micaKindV1Schema = makeJsonSchema(mapper, """
                $ref: %%STANDARD_PROPERTIES_REF%%
                properties:
                  kind:
                    type: string
                    enum: ["/mica/kind/v1"]
                  schema:
                    $ref: "%%JSON_SCHEMA_REF%%"
                required: ["kind", "schema"]
                """);

        this.micaViewV1Schema = makeJsonSchema(mapper, """
                $ref: %%STANDARD_PROPERTIES_REF%%
                properties:
                  kind:
                    type: string
                    enum: [ "/mica/view/v1" ]
                  parameters:
                    $ref: "%%JSON_SCHEMA_REF%%"
                  selector:
                    properties:
                      entityKind:
                        type: string
                    required: [ "entityKind" ]
                  data:
                    properties:
                      jsonPath:
                        type: string
                      jsonPatch:
                        type: array
                      flatten:
                        type: boolean
                      merge:
                        type: boolean
                    required: [ "jsonPath" ]
                  validation:
                    properties:
                      asEntityKind:
                        type: string
                    required: [ "asEntityKind" ]
                required: [ "kind", "selector", "data" ]
                """);

        // TODO disallow other properties
        // TODO fix jsonPatch type
    }

    /**
     * Standard properties of all entities (like "id" or "name").
     */
    public ObjectNode getStandardProperties() {
        return standardProperties.deepCopy();
    }

    /**
     * /mica/record/v1 - use to declare entities of any kind.
     */
    public ObjectNode getMicaRecordV1Schema() {
        return micaRecordV1Schema.deepCopy();
    }

    /**
     * /mica/kind/v1 - use to declare new entity kinds.
     */
    public ObjectNode getMicaKindV1Schema() {
        return micaKindV1Schema.deepCopy();
    }

    /**
     * /mica/view/v1 - use to declare entity views.
     */
    public ObjectNode getMicaViewV1Schema() {
        return micaViewV1Schema.deepCopy();
    }

    public Optional<JsonNode> get(String kind) {
        return switch (kind) {
            case STANDARD_PROPERTIES_V1 -> Optional.of(getStandardProperties());
            case MICA_RECORD_V1 -> Optional.of(getMicaRecordV1Schema());
            case MICA_KIND_V1 -> Optional.of(getMicaKindV1Schema());
            case MICA_VIEW_V1 -> Optional.of(getMicaViewV1Schema());
            default -> Optional.empty();
        };
    }

    public static ViewLike asViewLike(ObjectMapper objectMapper, EntityLike entity) {
        if (!entity.kind().equals(MICA_VIEW_V1)) {
            throw ApiException.badRequest("Expected a %s entity, got: %s".formatted(MICA_VIEW_V1, entity.kind()));
        }

        var name = entity.name();
        var parameters = Optional.ofNullable(entity.data().get("parameters")).filter(n -> !n.isNull());
        var selector = asViewLikeSelector(objectMapper, entity);
        var data = asViewLikeData(entity);
        var validation = asViewLikeValidation(entity);

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

    private static ViewLike.Data asViewLikeData(EntityLike entity) {
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

    private static <T> Optional<T> select(EntityLike entityLike,
                                          String pathElement1,
                                          String pathElement2,
                                          Function<JsonNode, T> converter) {
        // TODO validate target type
        return Optional.ofNullable(entityLike.data().get(pathElement1))
                .map(n -> n.get(pathElement2))
                .map(converter);
    }

    private static ObjectNode makeJsonSchema(ObjectMapper mapper, @Language("yaml") String yaml) {
        try {
            yaml = yaml.replace("%%JSON_SCHEMA_REF%%", JSON_SCHEMA_REF)
                    .replace("%%STANDARD_PROPERTIES_REF%%", STANDARD_PROPERTIES_REF);
            return mapper.readValue(yaml, ObjectNode.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
