package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityKindId;
import ca.ibodrov.mica.api.model.EntityKindVersion;
import ca.ibodrov.mica.api.model.PartialEntityKind;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.schema.ObjectSchemaNode;
import ca.ibodrov.mica.server.UuidGenerator;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Optional;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_ENTITY_KINDS;
import static org.jooq.JSONB.jsonb;
import static org.jooq.impl.DSL.currentOffsetDateTime;

public class EntityKindStore {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;
    private final UuidGenerator uuidGenerator;

    @Inject
    public EntityKindStore(@MicaDB DSLContext dsl,
                           ObjectMapper objectMapper,
                           UuidGenerator uuidGenerator) {

        this.dsl = dsl;
        this.objectMapper = objectMapper;
        this.uuidGenerator = uuidGenerator;
    }

    public boolean isKindExists(String kind) {
        return dsl.fetchExists(MICA_ENTITY_KINDS, MICA_ENTITY_KINDS.NAME.eq(kind));
    }

    public Optional<ObjectSchemaNode> getSchemaForKind(String kind) {
        return dsl.select(MICA_ENTITY_KINDS.SCHEMA).from(MICA_ENTITY_KINDS)
                .where(MICA_ENTITY_KINDS.NAME.eq(kind))
                .fetchOptional(MICA_ENTITY_KINDS.SCHEMA)
                .map(this::parseSchema);
    }

    public Optional<EntityKindVersion> upsert(PartialEntityKind entityKind) {
        var id = entityKind.id().map(EntityKindId::id)
                .orElseGet(uuidGenerator::generate);

        var schema = serializeSchema(entityKind.schema());

        return dsl.transactionResult(tx -> tx.dsl().insertInto(MICA_ENTITY_KINDS)
                .set(MICA_ENTITY_KINDS.ID, id)
                .set(MICA_ENTITY_KINDS.NAME, entityKind.name())
                .set(MICA_ENTITY_KINDS.SCHEMA, schema)
                .onConflict(MICA_ENTITY_KINDS.ID)
                .doUpdate()
                .set(MICA_ENTITY_KINDS.NAME, entityKind.name())
                .set(MICA_ENTITY_KINDS.SCHEMA, schema)
                .set(MICA_ENTITY_KINDS.UPDATED_AT, currentOffsetDateTime())
                .where(entityKind.updatedAt().map(MICA_ENTITY_KINDS.UPDATED_AT::eq).orElseGet(DSL::noCondition))
                .returning(MICA_ENTITY_KINDS.UPDATED_AT)
                .fetchOptional()
                .map(row -> new EntityKindVersion(new EntityKindId(id), row.getUpdatedAt())));
    }

    private ObjectSchemaNode parseSchema(JSONB jsonb) {
        try {
            return objectMapper.readValue(jsonb.data(), ObjectSchemaNode.class);
        } catch (IOException e) {
            throw new StoreException("Schema deserialization error", e);
        }
    }

    private JSONB serializeSchema(ObjectSchemaNode schema) {
        try {
            return jsonb(objectMapper.writeValueAsString(schema));
        } catch (IOException e) {
            throw new StoreException("Schema deserialization error", e);
        }
    }
}
