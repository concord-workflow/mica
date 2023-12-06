package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.schema.Validator;
import ca.ibodrov.mica.server.exceptions.ApiException;
import ca.ibodrov.mica.server.exceptions.EntityValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Inject;

import static java.util.Objects.requireNonNull;

public class EntityController {

    private final EntityStore entityStore;
    private final EntityKindStore entityKindStore;
    private final ObjectMapper objectMapper;

    @Inject
    public EntityController(EntityStore entityStore,
                            EntityKindStore entityKindStore,
                            ObjectMapper objectMapper) {

        this.entityStore = requireNonNull(entityStore);
        this.entityKindStore = requireNonNull(entityKindStore);
        this.objectMapper = requireNonNull(objectMapper);
    }

    public EntityVersion createOrUpdate(PartialEntity entity) {
        var kind = validateKind(entity.kind());

        var schema = entityKindStore.getSchemaForKind(kind)
                .orElseThrow(() -> ApiException.badRequest("Can't find schema for " + kind));

        var input = objectMapper.convertValue(entity, JsonNode.class);
        var validatedInput = Validator.validateObject(schema, input);
        if (!validatedInput.isValid()) {
            throw EntityValidationException.from(validatedInput);
        }

        if (entity.id().isEmpty()) {
            if (entityStore.isNameExists(entity.name())) {
                throw ApiException.badRequest("Entity with name '%s' already exists".formatted(entity.name()));
            }
        }

        // TODO check if there are any changes, return the same version if not

        return entityStore.upsert(entity)
                .orElseThrow(() -> ApiException.conflict("Version conflict: " + entity.name()));
    }

    private String validateKind(String kind) {
        if (kind == null || kind.isBlank()) {
            throw ApiException.badRequest("Missing 'kind'");
        }

        if (!entityKindStore.isKindExists(kind)) {
            throw ApiException.badRequest("Unknown kind: " + kind);
        }

        return kind;
    }
}
