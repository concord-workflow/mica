package ca.ibodrov.mica.server.data;

/*-
 * ~~~~~~
 * Mica
 * ------
 * Copyright (C) 2023 - 2025 Mica Authors
 * ------
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ======
 */

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
                             boolean replace,
                             Optional<UpdateIf> updateIf) {

        var kind = validateKind(entity.kind());

        var schema = entityKindStore.getSchemaForKind(kind)
                .orElseThrow(() -> ApiException.badRequest("Can't find schema for " + kind));

        var entityAsJsonObject = objectMapper.convertValue(entity, JsonNode.class);
        var validatedInput = validator.validateObject(schema, entityAsJsonObject);
        if (!validatedInput.isValid()) {
            throw validatedInput.toException();
        }

        return dsl.transactionResult(cfg -> {
            var tx = cfg.dsl();

            var newEntity = entity;

            if (replace) {
                entityStore.getVersion(tx, newEntity.name())
                        .ifPresent(version -> entityStore.killById(tx, version.id()));
            } else if (updateIf.isPresent() && updateIf.get() == UpdateIf.STRUCTURAL_DIFF) {
                var maybeExistingEntity = entityStore.getByName(tx, newEntity.name());
                if (maybeExistingEntity.isPresent()) {
                    var existingEntity = maybeExistingEntity.get();

                    if (Objects.equals(existingEntity.kind(), newEntity.kind())
                            && Objects.equals(existingEntity.data(), newEntity.data())) {
                        // no structural changes, return existing version
                        return existingEntity.version();
                    }

                    // structural changes, update the existing entity
                    newEntity = newEntity.withVersion(existingEntity.version());
                }
            }

            return createOrUpdate(tx, session, newEntity, doc, overwrite);
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

    private EntityVersion createOrUpdate(DSLContext tx,
                                         UserPrincipal session,
                                         PartialEntity entity,
                                         @Nullable String doc,
                                         boolean overwrite) {

        if (!overwrite) {
            // check if another entity already exists with the same name
            entityStore.getVersion(tx, entity.name()).ifPresent(version -> {
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

    private String validateKind(String kind) {
        if (kind == null || kind.isBlank()) {
            throw ApiException.badRequest("Missing 'kind'");
        }

        if (!entityKindStore.isKindExists(dsl, kind)) {
            throw ApiException.badRequest("Unknown kind: " + kind);
        }

        return kind;
    }

    public enum UpdateIf {

        /**
         * Perform a structural comparison of the existing entity and the new one.
         * Ignores comments, formatting and updatedAt.
         */
        STRUCTURAL_DIFF;

        public static Optional<UpdateIf> parse(String s) {
            if (s == null || s.isBlank()) {
                return Optional.empty();
            }
            if (s.equals("structuralDiff")) {
                return Optional.of(STRUCTURAL_DIFF);
            }
            throw new IllegalArgumentException("Unknown updateIf condition");
        }
    }
}
