package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.schema.Validator;
import ca.ibodrov.mica.server.exceptions.ApiException;
import ca.ibodrov.mica.server.exceptions.EntityValidationException;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.Objects.requireNonNull;

public class EntityController {

    private final EntityStore entityStore;
    private final EntityKindStore entityKindStore;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Inject
    public EntityController(EntityStore entityStore,
                            EntityKindStore entityKindStore,
                            ObjectMapper objectMapper) {

        this.entityStore = requireNonNull(entityStore);
        this.entityKindStore = requireNonNull(entityKindStore);
        this.objectMapper = requireNonNull(objectMapper);
        this.validator = new Validator(entityKindStore::getSchemaForKind);
    }

    public EntityVersion createOrUpdate(PartialEntity entity) {
        return createOrUpdate(entity, null);
    }

    public EntityVersion createOrUpdate(PartialEntity entity, @Nullable byte[] doc) {
        var kind = validateKind(entity.kind());

        var schema = entityKindStore.getSchemaForKind(kind)
                .orElseThrow(() -> ApiException.badRequest("Can't find schema for " + kind));

        var input = objectMapper.convertValue(entity, JsonNode.class);
        var validatedInput = validator.validateObject(schema, input);
        if (!validatedInput.isValid()) {
            throw EntityValidationException.from("Invalid entity", validatedInput);
        }

        var existingId = entityStore.getIdByName(entity.name());
        if (existingId.isPresent()) {
            if (entity.id().isEmpty() || !entity.id().equals(existingId)) {
                throw new StoreException("Entity '%s' already exists (with ID=%s)"
                        .formatted(entity.name(), existingId.get().toExternalForm()));
            }
        }

        // TODO check if there are any changes, return the same version if not

        return entityStore.upsert(entity, doc)
                .orElseThrow(() -> ApiException.conflict("Version conflict: " + entity.name()));
    }

    public void deleteIfExists(String name) {
        entityStore.getIdByName(name).ifPresent(entityStore::deleteById);
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
