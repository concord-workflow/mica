package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityId;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.db.jooq.enums.MicaHistoryOperationType;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_ENTITY_HISTORY;
import static java.util.Objects.requireNonNull;

public class EntityHistoryController {

    private final DSLContext dsl;

    @Inject
    public EntityHistoryController(@MicaDB DSLContext dsl) {
        this.dsl = requireNonNull(dsl);
    }

    public List<EntityHistoryEntry> list(EntityId entityId, int limit) {
        assert limit > 0;
        return dsl
                .select(MICA_ENTITY_HISTORY.ENTITY_ID, MICA_ENTITY_HISTORY.UPDATED_AT,
                        MICA_ENTITY_HISTORY.OPERATION_TYPE, MICA_ENTITY_HISTORY.AUTHOR)
                .from(MICA_ENTITY_HISTORY)
                .where(MICA_ENTITY_HISTORY.ENTITY_ID.eq(entityId.id()))
                .orderBy(MICA_ENTITY_HISTORY.UPDATED_AT.desc())
                .limit(limit)
                .fetch(r -> new EntityHistoryEntry(
                        new EntityId(r.get(MICA_ENTITY_HISTORY.ENTITY_ID)),
                        r.get(MICA_ENTITY_HISTORY.UPDATED_AT),
                        OperationType.valueOf(r.get(MICA_ENTITY_HISTORY.OPERATION_TYPE).name()),
                        r.get(MICA_ENTITY_HISTORY.AUTHOR)));
    }

    public void addEntry(DSLContext tx, EntityHistoryEntry entry, byte[] doc) {
        assert doc != null;
        tx.insertInto(MICA_ENTITY_HISTORY)
                .set(MICA_ENTITY_HISTORY.ENTITY_ID, entry.entityId().id())
                .set(MICA_ENTITY_HISTORY.UPDATED_AT, entry.updatedAt())
                .set(MICA_ENTITY_HISTORY.OPERATION_TYPE, MicaHistoryOperationType.valueOf(entry.operationType().name()))
                .set(MICA_ENTITY_HISTORY.AUTHOR, entry.author())
                .set(MICA_ENTITY_HISTORY.DOC, doc)
                .execute();
    }

    public Optional<byte[]> getHistoryDoc(EntityId entityId, Instant updatedAt) {
        return dsl.select(MICA_ENTITY_HISTORY.DOC)
                .from(MICA_ENTITY_HISTORY)
                .where(MICA_ENTITY_HISTORY.ENTITY_ID.eq(entityId.id())
                        .and(MICA_ENTITY_HISTORY.UPDATED_AT.eq(updatedAt)))
                .fetchOptional(MICA_ENTITY_HISTORY.DOC);
    }

    public enum OperationType {
        UPDATE,
        DELETE
    }

    public record EntityHistoryEntry(EntityId entityId, Instant updatedAt, OperationType operationType, String author) {
    }
}
