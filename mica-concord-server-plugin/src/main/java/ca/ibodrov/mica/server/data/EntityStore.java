package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.*;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.server.UuidGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record5;
import org.jooq.Record6;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_ENTITIES;
import static java.util.Objects.requireNonNull;
import static org.jooq.JSONB.jsonb;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.noCondition;

public class EntityStore {

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

    public List<EntityMetadata> search(String search) {
        var searchCondition = search != null ? MICA_ENTITIES.NAME.containsIgnoreCase(search) : noCondition();
        return dsl.select(MICA_ENTITIES.ID,
                MICA_ENTITIES.NAME,
                MICA_ENTITIES.KIND,
                MICA_ENTITIES.CREATED_AT,
                MICA_ENTITIES.UPDATED_AT)
                .from(MICA_ENTITIES)
                .where(searchCondition)
                .fetch(EntityStore::toEntityMetadata);
    }

    public Optional<Entity> getById(UUID entityId) {
        return dsl.select(MICA_ENTITIES.ID,
                MICA_ENTITIES.NAME,
                MICA_ENTITIES.KIND,
                MICA_ENTITIES.CREATED_AT,
                MICA_ENTITIES.UPDATED_AT,
                MICA_ENTITIES.DATA)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.ID.eq(entityId))
                .fetchOptional(this::toEntity);
    }

    public Optional<Entity> getByNameAndKind(String entityName, String entityKind) {
        return dsl.select(MICA_ENTITIES.ID,
                MICA_ENTITIES.NAME,
                MICA_ENTITIES.KIND,
                MICA_ENTITIES.CREATED_AT,
                MICA_ENTITIES.UPDATED_AT,
                MICA_ENTITIES.DATA)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.NAME.eq(entityName).and(MICA_ENTITIES.KIND.eq(entityKind)))
                .fetchOptional(this::toEntity);
    }

    public boolean isNameExists(String entityName) {
        return dsl.fetchExists(MICA_ENTITIES, MICA_ENTITIES.NAME.eq(entityName));
    }

    public boolean isNameAndKindExists(String entityName, String entityKind) {
        return dsl.fetchExists(MICA_ENTITIES, MICA_ENTITIES.NAME.eq(entityName).and(MICA_ENTITIES.KIND.eq(entityKind)));
    }

    public Optional<EntityVersion> upsert(PartialEntity entity) {
        var id = entity.id().map(EntityId::id)
                .orElseGet(uuidGenerator::generate);

        var data = jsonb(entity.data().toString());

        return dsl.transactionResult(tx -> tx.dsl().insertInto(MICA_ENTITIES)
                .set(MICA_ENTITIES.ID, id)
                .set(MICA_ENTITIES.NAME, entity.name())
                .set(MICA_ENTITIES.KIND, entity.kind())
                .set(MICA_ENTITIES.DATA, data)
                .onConflict(MICA_ENTITIES.ID)
                .doUpdate()
                .set(MICA_ENTITIES.NAME, entity.name())
                .set(MICA_ENTITIES.KIND, entity.kind())
                .set(MICA_ENTITIES.DATA, data)
                .set(MICA_ENTITIES.UPDATED_AT, currentOffsetDateTime())
                .where(entity.updatedAt().map(MICA_ENTITIES.UPDATED_AT::eq).orElseGet(DSL::noCondition))
                .returning(MICA_ENTITIES.UPDATED_AT)
                .fetchOptional()
                .map(row -> new EntityVersion(new EntityId(id), row.getUpdatedAt())));
    }

    private Entity toEntity(Record6<UUID, String, String, OffsetDateTime, OffsetDateTime, JSONB> record) {
        var id = new EntityId(record.value1());
        try {
            var data = objectMapper.readValue(record.value6().data(), JsonNode.class);
            return new Entity(id, record.value2(), record.value3(), record.value4(), record.value5(), data);
        } catch (IOException e) {
            // TODO do we need anything better here?
            throw new RuntimeException(e);
        }
    }

    private static EntityMetadata toEntityMetadata(Record5<UUID, String, String, OffsetDateTime, OffsetDateTime> record) {
        var id = new EntityId(record.value1());
        return new EntityMetadata(id, record.value2(), record.value3(), record.value4(), record.value5());
    }
}
