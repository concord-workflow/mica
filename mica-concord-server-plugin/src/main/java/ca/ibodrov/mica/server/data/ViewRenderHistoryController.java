package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityId;
import ca.ibodrov.mica.db.MicaDB;
import org.jooq.DSLContext;

import javax.inject.Inject;

import java.time.Duration;

import static ca.ibodrov.mica.db.jooq.tables.MicaViewRenderHistory.MICA_VIEW_RENDER_HISTORY;
import static java.util.Objects.requireNonNull;

public class ViewRenderHistoryController {

    private final DSLContext dsl;

    @Inject
    public ViewRenderHistoryController(@MicaDB DSLContext dsl) {
        this.dsl = requireNonNull(dsl);
    }

    public void addEntry(EntityId entityId, Duration selectTime, Duration renderTime, int fetchedEntities) {
        dsl.transaction(cfg -> {
            var tx = cfg.dsl();
            tx.insertInto(MICA_VIEW_RENDER_HISTORY)
                    .columns(MICA_VIEW_RENDER_HISTORY.ENTITY_ID,
                            MICA_VIEW_RENDER_HISTORY.SELECT_TIME_MS,
                            MICA_VIEW_RENDER_HISTORY.RENDER_TIME_MS,
                            MICA_VIEW_RENDER_HISTORY.FETCHED_ENTITIES)
                    .values(entityId.id(), selectTime.toMillis(), renderTime.toMillis(), fetchedEntities)
                    .execute();
        });
    }
}
