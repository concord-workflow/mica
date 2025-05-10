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
import static org.jooq.impl.DSL.currentInstant;

public class EntityHistoryController {

    private final DSLContext dsl;

    @Inject
    public EntityHistoryController(@MicaDB DSLContext dsl) {
        this.dsl = requireNonNull(dsl);
    }

    public List<EntityHistoryEntry> list(EntityId entityId, int limit) {
        var query = dsl
                .select(MICA_ENTITY_HISTORY.ENTITY_ID, MICA_ENTITY_HISTORY.UPDATED_AT,
                        MICA_ENTITY_HISTORY.OPERATION_TYPE, MICA_ENTITY_HISTORY.AUTHOR)
                .from(MICA_ENTITY_HISTORY)
                .where(MICA_ENTITY_HISTORY.ENTITY_ID.eq(entityId.id()))
                .orderBy(MICA_ENTITY_HISTORY.UPDATED_AT.desc());

        if (limit > 0) {
            query.limit(limit);
        }

        return query.fetch(r -> new EntityHistoryEntry(
                new EntityId(r.get(MICA_ENTITY_HISTORY.ENTITY_ID)),
                Optional.of(r.get(MICA_ENTITY_HISTORY.UPDATED_AT)),
                OperationType.valueOf(r.get(MICA_ENTITY_HISTORY.OPERATION_TYPE).name()),
                r.get(MICA_ENTITY_HISTORY.AUTHOR)));
    }

    public void addEntry(DSLContext tx, EntityHistoryEntry entry, Optional<String> doc) {
        var operationType = MicaHistoryOperationType.valueOf(entry.operationType().name());
        var query = tx.insertInto(MICA_ENTITY_HISTORY)
                .set(MICA_ENTITY_HISTORY.ENTITY_ID, entry.entityId().id())
                .set(MICA_ENTITY_HISTORY.OPERATION_TYPE, operationType)
                .set(MICA_ENTITY_HISTORY.AUTHOR, entry.author())
                .set(MICA_ENTITY_HISTORY.DOC, doc.orElse("n/a"));

        entry.updatedAt()
                .ifPresentOrElse(updatedAt -> query.set(MICA_ENTITY_HISTORY.UPDATED_AT, updatedAt),
                        () -> query.set(MICA_ENTITY_HISTORY.UPDATED_AT, currentInstant()));

        query.execute();
    }

    public Optional<String> getHistoryDoc(EntityId entityId, Instant updatedAt) {
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

    public record EntityHistoryEntry(EntityId entityId,
            Optional<Instant> updatedAt,
            OperationType operationType,
            String author) {
    }
}
