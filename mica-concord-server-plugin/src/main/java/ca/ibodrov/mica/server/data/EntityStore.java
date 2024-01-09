package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.*;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.server.UuidGenerator;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
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

    @Inject
    public EntityStore(@MicaDB DSLContext dsl,
                       ObjectMapper objectMapper,
                       UuidGenerator uuidGenerator) {

        this.dsl = requireNonNull(dsl);
        this.objectMapper = requireNonNull(objectMapper);
        this.uuidGenerator = requireNonNull(uuidGenerator);
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

    public Optional<EntityVersion> deleteById(EntityId entityId) {
        return dsl.transactionResult(tx -> tx.dsl()
                .deleteFrom(MICA_ENTITIES)
                .where(MICA_ENTITIES.ID.eq(entityId.id()))
                .returning(MICA_ENTITIES.ID, MICA_ENTITIES.UPDATED_AT)
                .fetchOptional()
                .map(r -> new EntityVersion(new EntityId(r.get(MICA_ENTITIES.ID)), r.get(MICA_ENTITIES.UPDATED_AT))));
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

    public Optional<EntityVersion> upsert(PartialEntity entity) {
        return upsert(entity, null);
    }

    public Optional<EntityVersion> upsert(PartialEntity entity, @Nullable byte[] doc) {
        return dsl.transactionResult(tx -> upsert(tx.dsl(), entity, doc));
    }

    public Optional<EntityVersion> upsert(DSLContext tx, PartialEntity entity) {
        return upsert(tx, entity, null);
    }

    public Optional<EntityVersion> upsert(DSLContext tx, PartialEntity entity, @Nullable byte[] doc) {
        var id = entity.id().map(EntityId::id)
                .orElseGet(uuidGenerator::generate);

        var updatedAt = getDatabaseInstant();
        var createdAt = entity.createdAt().orElse(updatedAt);

        // find and replace "id", "createdAt" and "updatedAt" properties in the doc
        // array
        // using substring replacement to preserve the original formatting and comments
        if (doc != null) {
            doc = inplaceUpdate(doc,
                    "id", objectMapper.convertValue(id, String.class),
                    "createdAt", objectMapper.convertValue(createdAt, String.class),
                    "updatedAt", objectMapper.convertValue(updatedAt, String.class));
        }

        var data = serializeData(entity.data());

        return tx.insertInto(MICA_ENTITIES)
                .set(MICA_ENTITIES.ID, id)
                .set(MICA_ENTITIES.NAME, entity.name())
                .set(MICA_ENTITIES.KIND, entity.kind())
                .set(MICA_ENTITIES.DATA, data)
                .set(MICA_ENTITIES.DOC, doc)
                .set(MICA_ENTITIES.CREATED_AT, createdAt)
                .set(MICA_ENTITIES.UPDATED_AT, updatedAt)
                .onConflict(MICA_ENTITIES.ID)
                .doUpdate()
                .set(MICA_ENTITIES.NAME, entity.name())
                .set(MICA_ENTITIES.KIND, entity.kind())
                .set(MICA_ENTITIES.DATA, data)
                .set(MICA_ENTITIES.DOC, doc)
                .set(MICA_ENTITIES.UPDATED_AT, updatedAt)
                .where(entity.updatedAt().map(MICA_ENTITIES.UPDATED_AT::eq).orElseGet(DSL::noCondition))
                .returning(MICA_ENTITIES.UPDATED_AT)
                .fetchOptional()
                .map(row -> new EntityVersion(new EntityId(id), row.getUpdatedAt()));
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
    public static byte[] inplaceUpdate(byte[] doc, String... kvs) {
        assert kvs != null && kvs.length % 2 == 0;
        var s = new String(doc, UTF_8);
        // update properties in the reverse order so that pre-pending a new key adds the
        // first key to the top
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

    private static EntityMetadata toEntityMetadata(Record5<UUID, String, String, Instant, Instant> record) {
        var id = new EntityId(record.value1());
        return new EntityMetadata(id, record.value2(), record.value3(), record.value4(), record.value5());
    }
}
