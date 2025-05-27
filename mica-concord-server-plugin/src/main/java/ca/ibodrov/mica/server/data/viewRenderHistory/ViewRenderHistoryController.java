package ca.ibodrov.mica.server.data.viewRenderHistory;

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
