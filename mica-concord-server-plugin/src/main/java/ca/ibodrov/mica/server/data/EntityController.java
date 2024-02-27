package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.exceptions.ApiException;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.walmartlabs.concord.server.security.UserPrincipal;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Arrays;

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
        this.validator = Validator.getDefault(objectMapper,
                new EntityKindStoreSchemaFetcher(entityKindStore, objectMapper));
    }

    @VisibleForTesting
    EntityVersion createOrUpdate(UserPrincipal session, PartialEntity entity) {
        return createOrUpdate(session, entity, null, false);
    }

    public EntityVersion createOrUpdate(UserPrincipal session,
                                        PartialEntity entity,
                                        @Nullable byte[] doc,
                                        boolean overwrite) {

        var kind = validateKind(entity.kind());

        var schema = entityKindStore.getSchemaForKind(kind)
                .orElseThrow(() -> ApiException.badRequest("Can't find schema for " + kind));

        var input = objectMapper.convertValue(entity, JsonNode.class);
        var validatedInput = validator.validateObject(schema, input);
        if (!validatedInput.isValid()) {
            throw validatedInput.toException();
        }

        var existingId = entityStore.getIdByName(entity.name());
        if (existingId.isPresent() && (entity.id().isEmpty() || !entity.id().equals(existingId))) {
            throw new StoreException("Entity '%s' already exists (with ID=%s)"
                    .formatted(entity.name(), existingId.get().toExternalForm()));
        }

        if (entity.id().isPresent() && entity.updatedAt().isPresent()) {
            var existingDoc = entityStore.getEntityDocById(entity.id().get(), entity.updatedAt().get());
            if (existingDoc.isPresent()) {
                if (Arrays.equals(existingDoc.get(), doc)) {
                    // no changes
                    return new EntityVersion(entity.id().get(), entity.updatedAt().get());
                }
            }
        }

        var newVersion = entityStore.upsert(session, entity, doc);
        if (newVersion.isEmpty() && overwrite) {
            newVersion = entityStore.upsert(session, entity.withoutUpdatedAt(), doc);
        }
        return newVersion.orElseThrow(() -> ApiException.conflict("Version conflict: " + entity.name()));
    }

    public void deleteIfExists(UserPrincipal session, String name) {
        entityStore.getIdByName(name).ifPresent(entityId -> entityStore.deleteById(session, entityId));
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
