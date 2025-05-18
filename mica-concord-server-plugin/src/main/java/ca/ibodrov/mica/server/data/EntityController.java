package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.DeletedEntityVersion;
import ca.ibodrov.mica.api.model.EntityId;
import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.server.data.EntityHistoryController.EntityHistoryEntry;
import ca.ibodrov.mica.server.exceptions.ApiException;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.jooq.DSLContext;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Objects;
import java.util.Optional;

import static ca.ibodrov.mica.server.data.EntityHistoryController.OperationType.DELETE;
import static ca.ibodrov.mica.server.data.EntityHistoryController.OperationType.UPDATE;
import static java.util.Objects.requireNonNull;

public class EntityController {

    private final DSLContext dsl;
    private final EntityStore entityStore;
    private final EntityKindStore entityKindStore;
    private final EntityHistoryController historyController;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Inject
    public EntityController(@MicaDB DSLContext dsl,
                            EntityStore entityStore,
                            EntityKindStore entityKindStore,
                            EntityHistoryController historyController,
                            ObjectMapper objectMapper) {

        this.dsl = requireNonNull(dsl);
        this.entityStore = requireNonNull(entityStore);
        this.entityKindStore = requireNonNull(entityKindStore);
        this.historyController = requireNonNull(historyController);
        this.objectMapper = requireNonNull(objectMapper);
        this.validator = Validator.getDefault(objectMapper,
                new EntityKindStoreSchemaFetcher(entityKindStore, objectMapper));
    }

    public EntityVersion put(UserPrincipal session,
                             PartialEntity entity,
                             String doc,
                             boolean overwrite,
                             boolean replace) {
        return dsl.transactionResult(cfg -> {
            var tx = cfg.dsl();
            if (replace) {
                entityStore.getVersion(tx, entity.name())
                        .ifPresent(version -> entityStore.deleteById(tx, version.id()));
            }
            return createOrUpdate(tx, session, entity, doc, overwrite);
        });
    }

    public Optional<DeletedEntityVersion> deleteById(UserPrincipal session, EntityId entityId) {
        var existingEntity = entityStore.getById(entityId);
        if (existingEntity.isEmpty()) {
            return Optional.empty();
        }

        if (existingEntity.get().name().startsWith("/mica/")) {
            // do not delete "system" entities
            throw ApiException.badRequest("A system entity (name starting with /mica/**) cannot be deleted.");
        }

        return dsl.transactionResult(cfg -> {
            var tx = cfg.dsl();
            var doc = entityStore.getLatestEntityDoc(tx, entityId);
            var version = entityStore.deleteById(tx, entityId);
            if (version.isPresent()) {
                var historyEntry = new EntityHistoryEntry(entityId, Optional.empty(), DELETE, session.getUsername());
                historyController.addEntry(tx, historyEntry, doc);
            }
            return version;
        });
    }

    @VisibleForTesting
    EntityVersion createOrUpdate(DSLContext tx,
                                 UserPrincipal session,
                                 PartialEntity entity,
                                 @Nullable String doc,
                                 boolean overwrite) {

        var kind = validateKind(tx, entity.kind());

        var schema = entityKindStore.getSchemaForKind(kind)
                .orElseThrow(() -> ApiException.badRequest("Can't find schema for " + kind));

        var input = objectMapper.convertValue(entity, JsonNode.class);

        // validate the input
        var validatedInput = validator.validateObject(schema, input);
        if (!validatedInput.isValid()) {
            throw validatedInput.toException();
        }

        if (!overwrite) {
            // check if another entity already exists with the same name
            entityStore.getVersion(entity.name()).ifPresent(version -> {
                if (entity.id().isEmpty() || !entity.id().get().equals(version.id())) {
                    throw new StoreException("Entity '%s' already exists (with ID=%s)"
                            .formatted(entity.name(), version.id().toExternalForm()));
                }
            });
        }

        // check if there are any changes
        var entityVersion = entity.version();
        if (entityVersion.isPresent()) {
            var existingDoc = entityStore.getEntityDoc(tx, entityVersion.get());
            if (existingDoc.isPresent() && Objects.equals(existingDoc.get(), doc)) {
                // no changes
                return new EntityVersion(entity.id().get(), entity.updatedAt().orElseThrow());
            }
        }

        var newVersion = entityStore.upsert(tx, entity, doc);
        if (newVersion.isEmpty() && overwrite) {
            newVersion = entityStore.upsert(tx, entity.withoutUpdatedAt(), doc);
        }

        var version = newVersion.orElseThrow(() -> ApiException.conflict("Version conflict: " + entity.name()));
        var author = session.getUsername();
        var historyEntry = new EntityHistoryEntry(version.id(), Optional.of(version.updatedAt()), UPDATE, author);
        historyController.addEntry(tx, historyEntry, Optional.ofNullable(doc));
        return version;
    }

    private String validateKind(DSLContext tx, String kind) {
        if (kind == null || kind.isBlank()) {
            throw ApiException.badRequest("Missing 'kind'");
        }

        if (!entityKindStore.isKindExists(tx, kind)) {
            throw ApiException.badRequest("Unknown kind: " + kind);
        }

        return kind;
    }
}
