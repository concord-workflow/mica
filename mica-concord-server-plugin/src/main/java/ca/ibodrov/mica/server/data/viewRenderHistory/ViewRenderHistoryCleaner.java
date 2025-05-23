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

import ca.ibodrov.mica.db.MicaDB;
import com.walmartlabs.concord.server.sdk.ScheduledTask;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static ca.ibodrov.mica.db.jooq.tables.MicaViewRenderHistory.MICA_VIEW_RENDER_HISTORY;
import static java.util.Objects.requireNonNull;

public class ViewRenderHistoryCleaner implements ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(ViewRenderHistoryCleaner.class);
    private static final long DEFAULT_CUTOFF_DAYS = 31 * 3;

    private final DSLContext dsl;

    @Inject
    public ViewRenderHistoryCleaner(@MicaDB DSLContext dsl) {
        this.dsl = requireNonNull(dsl);
    }

    @Override
    public String getId() {
        return "mica-view-render-history-cleaner";
    }

    @Override
    public long getIntervalInSec() {
        return Duration.ofDays(1).toSeconds();
    }

    @Override
    public void performTask() {
        var cutoff = Instant.now().minus(Duration.ofDays(DEFAULT_CUTOFF_DAYS)).truncatedTo(ChronoUnit.DAYS);
        log.info("Removing view render history entries older than {}", cutoff);
        int rows = dsl.deleteFrom(MICA_VIEW_RENDER_HISTORY)
                .where(MICA_VIEW_RENDER_HISTORY.RENDERED_AT.lessThan(cutoff))
                .execute();
        log.info("Removed {} row(s)", rows);
    }
}
