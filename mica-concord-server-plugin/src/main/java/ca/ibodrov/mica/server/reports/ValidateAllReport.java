package ca.ibodrov.mica.server.reports;

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.data.EntityFetchers;
import ca.ibodrov.mica.server.data.EntityKindStore;
import ca.ibodrov.mica.server.data.EntityKindStoreSchemaFetcher;
import ca.ibodrov.mica.server.data.Validator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.ValidationMessage;

import javax.inject.Inject;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ca.ibodrov.mica.api.kinds.MicaKindV1.MICA_KIND_V1;
import static java.util.Objects.requireNonNull;

/**
 * Reports unevaluated properties -- properties that exist in entities but are
 * not defined in their schema.
 */
public class ValidateAllReport implements Report<ValidateAllReport.Options> {

    private static final TypeReference<Map<String, JsonNode>> MAP_OF_JSON_NODES = new TypeReference<>() {
    };

    private final EntityKindStore entityKindStore;
    private final EntityFetchers entityFetchers;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Inject
    public ValidateAllReport(EntityKindStore entityKindStore,
                             EntityFetchers entityFetchers,
                             ObjectMapper objectMapper) {

        this.entityKindStore = requireNonNull(entityKindStore);
        this.entityFetchers = requireNonNull(entityFetchers);
        this.objectMapper = requireNonNull(objectMapper);
        var schemaFetcher = new EntityKindStoreSchemaFetcher(entityKindStore, objectMapper);
        this.validator = Validator.getDefault(objectMapper, schemaFetcher);
    }

    @Override
    public PartialEntity run(Options options) {
        // grab all "/mica/kind/v1" entities
        var report = entityFetchers.fetchAll(MICA_KIND_V1)
                .map(kindAndSource -> {
                    var kind = kindAndSource.entity();
                    var kindSource = kindAndSource.source();

                    // check if the schema is present
                    // TODO get rid of EntityKindStore, normalize schemas in common code
                    var maybeSchema = entityKindStore.getSchemaForKind(kind.name()).orElse(null);
                    if (maybeSchema == null) {
                        return ReportEntry.badSchema(kindSource, kind.name(),
                                "Can't find schema for %s".formatted(kind.name()));
                    }

                    // disallow unevaluated properties
                    var schemaJson = (ObjectNode) maybeSchema;
                    if (options.reportUnevaluatedProperties()) {
                        schemaJson.set("unevaluatedProperties", BooleanNode.FALSE);
                    }

                    // parse the schema
                    var schemaFactory = validator.getJsonSchemaFactory();
                    var config = new SchemaValidatorsConfig();
                    config.setPathType(PathType.JSON_POINTER);
                    var schema = schemaFactory.getSchema(schemaJson, config);

                    // validate each entity of this kind
                    var entities = entityFetchers.fetchAll(kind.name()).map(entityAndSource -> {
                        var entity = entityAndSource.entity();
                        var input = objectMapper.convertValue(entity, JsonNode.class);
                        var violations = schema.validate(input).stream()
                                .map(message -> asViolation(input, message))
                                .toList();
                        return new EntityEntry(entity.name(), violations);
                    })
                            .filter(e -> !e.violations().isEmpty())
                            .toList();

                    return new ReportEntry(kindSource, kind.name(), List.of(), entities);
                })
                .filter(e -> !e.violations().isEmpty() || !e.entities().isEmpty())
                .toList();

        var data = Map.<String, List<?>>of("report", report);
        return PartialEntity.create(
                "/mica/reports/unevaluated-properties",
                "/mica/report/v1",
                objectMapper.convertValue(data, MAP_OF_JSON_NODES));
    }

    private static Violation asViolation(JsonNode input, ValidationMessage message) {
        var path = Optional.ofNullable(message.getPath());
        var example = path.map(pointer -> {
            try {
                return input.at(pointer);
            } catch (Exception e) {
                return TextNode.valueOf("Can't get %s: %s".formatted(pointer, e.getMessage()));
            }
        });
        return new Violation(message.getType(), message.getMessage(), path, example);
    }

    private record ReportEntry(Optional<URI> storeUri, String kind, List<Violation> violations,
            List<EntityEntry> entities) {

        static ReportEntry badSchema(Optional<URI> storeUri, String kind, String message) {
            return new ReportEntry(storeUri, kind, List.of(Violation.badSchema(message)), List.of());
        }
    }

    private record EntityEntry(String entityName, List<Violation> violations) {
    }

    private record Violation(String type, String message, Optional<String> path, Optional<JsonNode> example) {

        static Violation badSchema(String message) {
            return new Violation("micaBadSchema", message, Optional.empty(), Optional.empty());
        }
    }

    public record Options(boolean reportUnevaluatedProperties) implements Report.Options {
    }
}
