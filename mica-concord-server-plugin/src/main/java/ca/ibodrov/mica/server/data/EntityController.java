package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.schema.Validator;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Inject;
import java.util.Map;

import static ca.ibodrov.mica.server.exceptions.ApiException.ErrorKind.BAD_DATA;
import static ca.ibodrov.mica.server.exceptions.ApiException.ErrorKind.UNKNOWN_ENTITY_KIND;
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
                .orElseThrow(() -> ApiException.badRequest(UNKNOWN_ENTITY_KIND, "Can't find schema for " + kind));

        var input = objectMapper.convertValue(entity, Map.class);
        var result = new Validator(objectMapper).validateMap(schema, input);
        if (!result.isValid()) {
            throw ApiException.badRequest(BAD_DATA, "Validation errors: " + result);
        }

        if (entity.id().isEmpty()) {
            if (entityStore.isNameExists(entity.name())) {
                throw ApiException.badRequest(BAD_DATA,
                        "Entity with name '%s' already exists".formatted(entity.name()));
            }
        }

        // TODO check if there are any changes, return the same version if not

        return entityStore.upsert(entity)
                .orElseThrow(() -> ApiException.conflict(BAD_DATA, "Version conflict: " + entity.name()));
    }

    public String validateKind(String kind) {
        if (kind == null || kind.isBlank()) {
            throw ApiException.badRequest(BAD_DATA, "Missing 'kind'");
        }

        if (!entityKindStore.isKindExists(kind)) {
            throw ApiException.badRequest(UNKNOWN_ENTITY_KIND, "Unknown kind: " + kind);
        }

        return kind;
    }
}
