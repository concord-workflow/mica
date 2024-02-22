package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.*;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.db.jooq.tables.records.MicaEntitiesRecord;
import ca.ibodrov.mica.server.UuidGenerator;
import ca.ibodrov.mica.server.data.EntityHistoryController.EntityHistoryEntry;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_ENTITIES;
import static ca.ibodrov.mica.server.data.EntityHistoryController.OperationType.DELETE;
import static ca.ibodrov.mica.server.data.EntityHistoryController.OperationType.UPDATE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.jooq.JSONB.jsonb;
import static org.jooq.impl.DSL.noCondition;

/**
 * TODO use name + kind as unique key
 */
public class EntityStore {

    private static final TypeReference<Map<String, JsonNode>> PROPERTIES_TYPE = new TypeReference<>() {
    };

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;
    private final UuidGenerator uuidGenerator;
    private final EntityHistoryController historyController;

    @Inject
    public EntityStore(@MicaDB DSLContext dsl,
                       ObjectMapper objectMapper,
                       UuidGenerator uuidGenerator,
                       EntityHistoryController historyController) {

        this.dsl = requireNonNull(dsl);
        this.objectMapper = requireNonNull(objectMapper);
        this.uuidGenerator = requireNonNull(uuidGenerator);
        this.historyController = requireNonNull(historyController);
    }

    public record ListEntitiesRequest(@Nullable String search,
            @Nullable String entityNameStartsWith,
            @Nullable String entityName,
            @Nullable String entityKind,
            @Nullable OrderBy orderBy,
            int limit) {
    }

    public List<EntityMetadata> search(ListEntitiesRequest request) {
        var search = request.search();
        var searchCondition = search != null ? MICA_ENTITIES.NAME.containsIgnoreCase(search) : noCondition();

        var entityNameStartsWith = request.entityNameStartsWith();
        var entityNameStartsWithCondition = entityNameStartsWith != null
                ? MICA_ENTITIES.NAME.startsWith(entityNameStartsWith)
                : noCondition();

        var entityName = request.entityName();
        var nameCondition = entityName != null ? MICA_ENTITIES.NAME.eq(entityName) : noCondition();

        var entityKind = request.entityKind();
        var entityKindCondition = entityKind != null ? MICA_ENTITIES.KIND.eq(entityKind) : noCondition();

        var query = dsl.select(MICA_ENTITIES.ID,
                MICA_ENTITIES.NAME,
                MICA_ENTITIES.KIND,
                MICA_ENTITIES.CREATED_AT,
                MICA_ENTITIES.UPDATED_AT)
                .from(MICA_ENTITIES)
                .where(searchCondition
                        .and(entityNameStartsWithCondition)
                        .and(nameCondition)
                        .and(entityKindCondition));

        var orderBy = request.orderBy();
        if (orderBy != null) {
            switch (orderBy) {
                case NAME -> query.orderBy(MICA_ENTITIES.NAME);
            }
        }

        var limit = request.limit();
        if (limit > 0) {
            query.limit(limit);
        }

        return query
                .fetch(EntityStore::toEntityMetadata);
    }

    public Optional<Entity> getById(EntityId entityId) {
        return getById(entityId, null);
    }

    public Optional<Entity> getById(EntityId entityId, @Nullable Instant updatedAt) {
        var query = dsl.select(MICA_ENTITIES.ID,
                MICA_ENTITIES.NAME,
                MICA_ENTITIES.KIND,
                MICA_ENTITIES.CREATED_AT,
                MICA_ENTITIES.UPDATED_AT,
                MICA_ENTITIES.DATA)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.ID.eq(entityId.id()));

        if (updatedAt != null) {
            query = query.and(MICA_ENTITIES.UPDATED_AT.eq(updatedAt));
        }

        return query.fetchOptional(this::toEntity);
    }

    public Optional<Entity> getByName(String entityName) {
        return dsl.select(MICA_ENTITIES.ID,
                MICA_ENTITIES.NAME,
                MICA_ENTITIES.KIND,
                MICA_ENTITIES.CREATED_AT,
                MICA_ENTITIES.UPDATED_AT,
                MICA_ENTITIES.DATA)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.NAME.eq(entityName))
                .fetchOptional(this::toEntity);
    }

    public Optional<EntityId> getIdByName(String entityName) {
        return dsl.select(MICA_ENTITIES.ID)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.NAME.eq(entityName))
                .fetchOptional(r -> new EntityId(r.value1()));
    }

    public Optional<byte[]> getEntityDocById(EntityId entityId, @Nullable Instant updatedAt) {
        var query = dsl.select(MICA_ENTITIES.DOC)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.ID.eq(entityId.id()));

        if (updatedAt != null) {
            query = query.and(MICA_ENTITIES.UPDATED_AT.eq(updatedAt));
        }

        return query.fetchOptional(Record1::value1);
    }

    public Optional<EntityVersion> deleteById(UserPrincipal session, EntityId entityId) {
        return dsl.transactionResult(tx -> {
            var version = tx.dsl()
                    .deleteFrom(MICA_ENTITIES)
                    .where(MICA_ENTITIES.ID.eq(entityId.id()))
                    .returning(MICA_ENTITIES.ID, MICA_ENTITIES.UPDATED_AT, MICA_ENTITIES.DOC)
                    .fetchOptional();

            if (version.isPresent()) {
                var doc = version.map(MicaEntitiesRecord::getDoc).orElseGet(() -> "n/a".getBytes(UTF_8));
                historyController.addEntry(tx.dsl(),
                        new EntityHistoryEntry(entityId, getDatabaseInstant(), DELETE, session.getUsername()), doc);
            }

            return version.map(r -> new EntityVersion(new EntityId(r.get(MICA_ENTITIES.ID)),
                    r.get(MICA_ENTITIES.UPDATED_AT)));
        });
    }

    public List<EntityVersionAndName> deleteByNamePatterns(List<String> namePatterns) {
        assert namePatterns != null && !namePatterns.isEmpty();
        return dsl.transactionResult(tx -> {
            var query = tx.dsl()
                    .select(MICA_ENTITIES.ID, MICA_ENTITIES.NAME, MICA_ENTITIES.UPDATED_AT)
                    .from(MICA_ENTITIES)
                    .where(MICA_ENTITIES.NAME.likeRegex(namePatterns.get(0)));

            for (int i = 1; i < namePatterns.size(); i++) {
                query = query.or(MICA_ENTITIES.NAME.likeRegex(namePatterns.get(i)));
            }

            return query
                    .forUpdate()
                    .skipLocked()
                    .fetch()
                    .stream().flatMap(r -> {
                        var id = r.value1();
                        var name = r.value2();
                        var updatedAt = r.value3();
                        var rows = tx.dsl().deleteFrom(MICA_ENTITIES)
                                .where(MICA_ENTITIES.ID.eq(id)
                                        .and(MICA_ENTITIES.UPDATED_AT.eq(updatedAt)))
                                .execute();
                        if (rows == 0) {
                            return Stream.empty();
                        }
                        return Stream.of(new EntityVersionAndName(new EntityId(id), updatedAt, name));
                    })
                    .toList();
        });
    }

    public boolean isNameAndKindExists(String entityName, String entityKind) {
        return dsl.fetchExists(MICA_ENTITIES, MICA_ENTITIES.NAME.eq(entityName).and(MICA_ENTITIES.KIND.eq(entityKind)));
    }

    public Optional<EntityVersion> upsert(UserPrincipal session, PartialEntity entity) {
        return dsl.transactionResult(tx -> upsert(tx.dsl(), entity, session.getUsername(), null));
    }

    public Optional<EntityVersion> upsert(UserPrincipal session, PartialEntity entity, @Nullable byte[] doc) {
        return dsl.transactionResult(tx -> upsert(tx.dsl(), entity, session.getUsername(), doc));
    }

    public Optional<EntityVersion> upsert(DSLContext tx, UserPrincipal session, PartialEntity entity) {
        return upsert(tx, entity, session.getUsername(), null);
    }

    private Optional<EntityVersion> upsert(DSLContext tx, PartialEntity entity, String author, @Nullable byte[] doc) {
        var name = normalizeName(entity.name());

        if (isNameUsedAsPathElsewhere(tx, name)) {
            throw new StoreException(entity.name() + " is a folder, cannot create an entity with the same name");
        }

        var id = entity.id().map(EntityId::id)
                .orElseGet(uuidGenerator::generate);

        var updatedAt = getDatabaseInstant();
        var createdAt = entity.createdAt().orElse(updatedAt);

        // find and replace "id", "name", "createdAt" and "updatedAt" properties in the
        // doc
        // array using substring replacement to preserve the original formatting and
        // comments
        if (doc != null) {
            doc = inplaceUpdate(doc,
                    "id", objectMapper.convertValue(id, String.class),
                    "name", name,
                    "createdAt", objectMapper.convertValue(createdAt, String.class),
                    "updatedAt", objectMapper.convertValue(updatedAt, String.class));
        } else {
            doc = "n/a".getBytes(UTF_8);
        }

        var data = serializeData(entity.data());

        var version = tx.insertInto(MICA_ENTITIES)
                .set(MICA_ENTITIES.ID, id)
                .set(MICA_ENTITIES.NAME, name)
                .set(MICA_ENTITIES.KIND, entity.kind())
                .set(MICA_ENTITIES.DATA, data)
                .set(MICA_ENTITIES.DOC, doc)
                .set(MICA_ENTITIES.CREATED_AT, createdAt)
                .set(MICA_ENTITIES.UPDATED_AT, updatedAt)
                .onConflict(MICA_ENTITIES.ID)
                .doUpdate()
                .set(MICA_ENTITIES.NAME, name)
                .set(MICA_ENTITIES.KIND, entity.kind())
                .set(MICA_ENTITIES.DATA, data)
                .set(MICA_ENTITIES.DOC, doc)
                .set(MICA_ENTITIES.UPDATED_AT, updatedAt)
                .where(entity.updatedAt().map(MICA_ENTITIES.UPDATED_AT::eq).orElseGet(DSL::noCondition))
                .returning(MICA_ENTITIES.UPDATED_AT)
                .fetchOptional()
                .map(row -> new EntityVersion(new EntityId(id), row.getUpdatedAt()));

        historyController.addEntry(tx, new EntityHistoryEntry(new EntityId(id), updatedAt, UPDATE, author), doc);

        return version;
    }

    private Entity toEntity(Record6<UUID, String, String, Instant, Instant, JSONB> record) {
        return toEntity(objectMapper, record);
    }

    private JSONB serializeData(Map<String, JsonNode> data) {
        try {
            return jsonb(objectMapper.writeValueAsString(data));
        } catch (IOException e) {
            throw new StoreException("JSON serialization error, most likely a bug: " + e.getMessage(), e);
        }
    }

    private boolean isNameUsedAsPathElsewhere(DSLContext tx, String name) {
        var path = name + "/";
        return tx.fetchExists(MICA_ENTITIES, MICA_ENTITIES.NAME.startsWith(path));
    }

    private Instant getDatabaseInstant() {
        return dsl.select(DSL.currentTimestamp()).fetchOne(r -> r.value1().toInstant());
    }

    public static Entity toEntity(ObjectMapper objectMapper,
                                  Record6<UUID, String, String, Instant, Instant, JSONB> record) {
        var id = new EntityId(record.value1());
        try {
            var data = objectMapper.readValue(record.value6().data(), PROPERTIES_TYPE);
            return new Entity(id, record.value2(), record.value3(), record.value4(), record.value5(), data);
        } catch (IOException e) {
            throw new StoreException("JSON deserialization error, most likely a bug: " + e.getMessage(), e);
        }
    }

    @VisibleForTesting
    static byte[] inplaceUpdate(byte[] doc, String... kvs) {
        assert kvs != null && kvs.length % 2 == 0;
        var s = new String(doc, UTF_8);
        for (int i = kvs.length - 2; i >= 0; i -= 2) {
            var k = kvs[i];
            var v = kvs[i + 1];
            if (s.contains(k + ":")) {
                // update the existing key
                s = s.replaceFirst("(?m)^" + k + ":.*$", "%s: \"%s\"".formatted(k, v));
            } else {
                // or pre-pend a new key
                s = "%s: \"%s\"\n%s".formatted(k, v, s);
            }
        }
        return s.getBytes(UTF_8);
    }

    @VisibleForTesting
    static String normalizeName(String name) {
        return name.replaceAll("//+", "/");
    }

    private static EntityMetadata toEntityMetadata(Record5<UUID, String, String, Instant, Instant> record) {
        var id = new EntityId(record.value1());
        return new EntityMetadata(id, record.value2(), record.value3(), record.value4(), record.value5());
    }
}
