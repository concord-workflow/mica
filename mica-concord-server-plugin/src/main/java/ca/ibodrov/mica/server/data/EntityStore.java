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
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_ENTITIES;
import static java.util.Objects.requireNonNull;
import static org.jooq.JSONB.jsonb;
import static org.jooq.impl.DSL.currentInstant;
import static org.jooq.impl.DSL.noCondition;

public class EntityStore {

    private static final TypeReference<LinkedHashMap<String, JsonNode>> PROPERTIES_TYPE = new TypeReference<>() {
    };

    private static final int MAX_NAME_PATTERNS = 32;

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
            @Nullable OrderBy orderBy) {
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

        // search should NOT return "deleted" entities
        var notDeleted = MICA_ENTITIES.DELETED_AT.isNull();

        var query = dsl.select(MICA_ENTITIES.ID,
                MICA_ENTITIES.NAME,
                MICA_ENTITIES.KIND,
                MICA_ENTITIES.CREATED_AT,
                MICA_ENTITIES.UPDATED_AT)
                .from(MICA_ENTITIES)
                .where(searchCondition
                        .and(entityNameStartsWithCondition)
                        .and(nameCondition)
                        .and(entityKindCondition)
                        .and(notDeleted));

        var orderBy = request.orderBy();
        if (orderBy != null) {
            if (orderBy == OrderBy.NAME) {
                query.orderBy(MICA_ENTITIES.NAME);
            }
        }

        return query
                .fetch(EntityStore::toEntityMetadata);
    }

    public Optional<Entity> getById(EntityId entityId) {
        return getById(entityId, null);
    }

    public Optional<Entity> getById(EntityId entityId, @Nullable Instant updatedAt) {
        // not adding the "DELETED_AT is not null" condition
        // this method should be able to retrieve "deleted" entities
        var query = dsl.select(MICA_ENTITIES.ID,
                MICA_ENTITIES.NAME,
                MICA_ENTITIES.KIND,
                MICA_ENTITIES.CREATED_AT,
                MICA_ENTITIES.UPDATED_AT,
                MICA_ENTITIES.DELETED_AT,
                MICA_ENTITIES.DATA)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.ID.eq(entityId.id()));

        if (updatedAt != null) {
            query = query.and(MICA_ENTITIES.UPDATED_AT.eq(updatedAt));
        }

        return query.fetchOptional(this::toEntity);
    }

    public Optional<Entity> getByName(String entityName) {
        return getByName(dsl, entityName);
    }

    public Optional<Entity> getByName(DSLContext tx, String entityName) {
        // this method should NOT return "deleted" entities
        return tx.select(MICA_ENTITIES.ID,
                MICA_ENTITIES.NAME,
                MICA_ENTITIES.KIND,
                MICA_ENTITIES.CREATED_AT,
                MICA_ENTITIES.UPDATED_AT,
                MICA_ENTITIES.DELETED_AT, // should be null
                MICA_ENTITIES.DATA)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.DELETED_AT.isNull().and(MICA_ENTITIES.NAME.eq(entityName)))
                .fetchOptional(this::toEntity);
    }

    public Optional<EntityVersion> getVersion(String entityName) {
        return getVersion(dsl, entityName);
    }

    public Optional<EntityVersion> getVersion(DSLContext tx, String entityName) {
        // this method should NOT return "deleted" entities
        return tx.select(MICA_ENTITIES.ID, MICA_ENTITIES.UPDATED_AT)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.DELETED_AT.isNull().and(MICA_ENTITIES.NAME.eq(entityName)))
                .fetchOptional(r -> new EntityVersion(new EntityId(r.value1()), r.value2()));
    }

    public Optional<String> getLatestEntityDoc(EntityId entityId) {
        return getLatestEntityDoc(dsl, entityId);
    }

    public Optional<String> getLatestEntityDoc(DSLContext tx, EntityId entityId) {
        // this method should NOT return "deleted" entities
        return tx.select(MICA_ENTITIES.DOC)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.DELETED_AT.isNull().and(MICA_ENTITIES.ID.eq(entityId.id())))
                .orderBy(MICA_ENTITIES.UPDATED_AT.desc())
                .limit(1)
                .fetchOptional(Record1::value1);
    }

    public Optional<String> getEntityDoc(EntityVersion version) {
        return getEntityDoc(dsl, version);
    }

    public Optional<String> getEntityDoc(DSLContext tx, EntityVersion version) {
        // this method should NOT return "deleted" entities
        return tx.select(MICA_ENTITIES.DOC)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.DELETED_AT.isNull()
                        .and(MICA_ENTITIES.ID.eq(version.id().id()))
                        .and(MICA_ENTITIES.UPDATED_AT.eq(version.updatedAt())))
                .fetchOptional(Record1::value1);
    }

    public Optional<DeletedEntityVersion> deleteById(DSLContext tx, EntityId entityId) {
        var version = tx.update(MICA_ENTITIES)
                .set(MICA_ENTITIES.DELETED_AT, currentInstant())
                .where(MICA_ENTITIES.ID.eq(entityId.id()))
                .returning(MICA_ENTITIES.ID, MICA_ENTITIES.UPDATED_AT, MICA_ENTITIES.DELETED_AT, MICA_ENTITIES.DOC)
                .fetchOptional();

        return version.map(r -> new DeletedEntityVersion(new EntityId(r.get(MICA_ENTITIES.ID)),
                r.get(MICA_ENTITIES.UPDATED_AT),
                r.get(MICA_ENTITIES.DELETED_AT)));
    }

    public List<DeletedEntityVersionAndName> deleteByNamePatterns(List<String> namePatterns) {
        assert namePatterns != null && !namePatterns.isEmpty();
        assert namePatterns.size() <= MAX_NAME_PATTERNS;

        return dsl.transactionResult(cfg -> {
            var tx = cfg.dsl();

            var query = tx.select(MICA_ENTITIES.ID, MICA_ENTITIES.NAME, MICA_ENTITIES.UPDATED_AT)
                    .from(MICA_ENTITIES)
                    .where(MICA_ENTITIES.NAME.likeRegex(namePatterns.get(0)));

            for (int i = 1; i < namePatterns.size(); i++) {
                query = query.or(MICA_ENTITIES.NAME.likeRegex(namePatterns.get(i)));
            }

            var deletedAt = getDatabaseInstant(tx);

            return query
                    .forUpdate()
                    .skipLocked()
                    .fetch()
                    .stream().flatMap(r -> {
                        var id = r.value1();
                        var name = r.value2();
                        var updatedAt = r.value3();
                        var rows = tx.update(MICA_ENTITIES)
                                .set(MICA_ENTITIES.DELETED_AT, deletedAt)
                                .where(MICA_ENTITIES.ID.eq(id)
                                        .and(MICA_ENTITIES.UPDATED_AT.eq(updatedAt)))
                                .execute();
                        if (rows == 0) {
                            return Stream.empty();
                        }
                        return Stream.of(new DeletedEntityVersionAndName(new EntityId(id), updatedAt, deletedAt, name));
                    })
                    .toList();
        });
    }

    public boolean isNameAndKindExists(DSLContext tx, String entityName, String entityKind) {
        // this method should NOT return "deleted" entities
        return tx.fetchExists(MICA_ENTITIES,
                MICA_ENTITIES.DELETED_AT.isNull()
                        .and(MICA_ENTITIES.NAME.eq(entityName)
                                .and(MICA_ENTITIES.KIND.eq(entityKind))));
    }

    public Optional<EntityVersion> upsert(DSLContext tx,
                                          PartialEntity entity,
                                          @Nullable String doc) {
        var name = normalizeName(entity.name());

        if (isNameUsedAsPathElsewhere(tx, name)) {
            throw new StoreException(entity.name() + " is a folder, cannot create an entity with the same name");
        }

        var id = entity.id().map(EntityId::id)
                .orElseGet(uuidGenerator::generate);
        var kind = entity.kind();
        var updatedAt = getDatabaseInstant(tx);
        var createdAt = entity.createdAt().orElse(updatedAt);

        // find and replace "id", "name", "createdAt" and "updatedAt" properties in the
        // doc using substring replacement to preserve the original formatting and
        // comments
        if (doc != null) {
            doc = inplaceUpdate(doc,
                    "id", objectMapper.convertValue(id, String.class),
                    "name", name,
                    "kind", kind,
                    "createdAt", objectMapper.convertValue(createdAt, String.class),
                    "updatedAt", objectMapper.convertValue(updatedAt, String.class));

            var deletedAt = entity.deletedAt();
            if (deletedAt.isPresent()) {
                doc = inplaceUpdate(doc, "deletedAt", objectMapper.convertValue(deletedAt.get(), String.class));
            }
        }

        var data = serializeData(entity.data());

        return tx.insertInto(MICA_ENTITIES)
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
    }

    private Entity toEntity(Record7<UUID, String, String, Instant, Instant, Instant, JSONB> record) {
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
        // this method should NOT return "deleted" entities
        var path = name + "/";
        return tx.fetchExists(MICA_ENTITIES,
                MICA_ENTITIES.DELETED_AT.isNull()
                        .and(MICA_ENTITIES.NAME.startsWith(path)));
    }

    private Instant getDatabaseInstant(DSLContext tx) {
        return tx.select(DSL.currentTimestamp()).fetchOne(r -> r.value1().toInstant());
    }

    public static Entity toEntity(ObjectMapper objectMapper,
                                  Record7<UUID, String, String, Instant, Instant, Instant, JSONB> record) {
        var id = new EntityId(record.value1());
        try {
            var data = objectMapper.readValue(record.value7().data(), PROPERTIES_TYPE);
            return new Entity(id, record.value2(), record.value3(), record.value4(), record.value5(),
                    Optional.ofNullable(record.value6()), data);
        } catch (IOException e) {
            throw new StoreException("JSON deserialization error, most likely a bug: " + e.getMessage(), e);
        }
    }

    @VisibleForTesting
    static String inplaceUpdate(String s, String... kvs) {
        assert kvs != null && kvs.length % 2 == 0;
        for (int i = kvs.length - 2; i >= 0; i -= 2) {
            var k = kvs[i];
            var v = kvs[i + 1];
            var existingKey = Pattern.compile("(?m)^" + k + ":.*$").matcher(s);
            if (existingKey.find()) {
                // update the existing key
                s = existingKey.replaceFirst("%s: \"%s\"".formatted(k, v));
            } else {
                // or pre-pend a new key
                s = "%s: \"%s\"\n%s".formatted(k, v, s);
            }
        }
        return s;
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
