package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.db.MicaDB;
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

import static java.util.Objects.requireNonNull;

public class EntityController {

    private final DSLContext dsl;
    private final EntityStore entityStore;
    private final EntityKindStore entityKindStore;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Inject
    public EntityController(@MicaDB DSLContext dsl,
                            EntityStore entityStore,
                            EntityKindStore entityKindStore,
                            ObjectMapper objectMapper) {
        this.dsl = requireNonNull(dsl);
        this.entityStore = requireNonNull(entityStore);
        this.entityKindStore = requireNonNull(entityKindStore);
        this.objectMapper = requireNonNull(objectMapper);
        this.validator = Validator.getDefault(objectMapper,
                new EntityKindStoreSchemaFetcher(entityKindStore, objectMapper));
    }

    @VisibleForTesting
    EntityVersion createOrUpdate(UserPrincipal session, PartialEntity entity) {
        return dsl.transactionResult(tx -> createOrUpdate(tx.dsl(), session, entity, null, false));
    }

    @VisibleForTesting
    EntityVersion createOrUpdate(UserPrincipal session,
                                 PartialEntity entity,
                                 @Nullable String doc,
                                 boolean overwrite) {
        return dsl.transactionResult(tx -> createOrUpdate(tx.dsl(), session, entity, doc, overwrite));
    }

    public EntityVersion createOrUpdate(DSLContext tx,
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

        var newVersion = entityStore.upsert(tx, session, entity, doc);
        if (newVersion.isEmpty() && overwrite) {
            newVersion = entityStore.upsert(tx, session, entity.withoutUpdatedAt(), doc);
        }

        return newVersion.orElseThrow(() -> ApiException.conflict("Version conflict: " + entity.name()));
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
                        // TODO deleteByVersion?
                        .ifPresent(version -> entityStore.deleteById(tx, session, version.id()));
            }
            return createOrUpdate(tx, session, entity, doc, overwrite);
        });
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
