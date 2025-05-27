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

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.server.data.EntityFetcher;
import ca.ibodrov.mica.server.data.QueryParams;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.jooq.DSLContext;
import org.jooq.Record6;

import javax.inject.Inject;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static ca.ibodrov.mica.db.jooq.tables.MicaEntities.MICA_ENTITIES;
import static ca.ibodrov.mica.db.jooq.tables.MicaViewRenderHistory.MICA_VIEW_RENDER_HISTORY;

public class ViewRenderHistoryEntityFetcher implements EntityFetcher {

    private static final URI DEFAULT_URI = URI.create("mica://viewRenderHistory");

    private final DSLContext dsl;

    @Inject
    public ViewRenderHistoryEntityFetcher(@MicaDB DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<URI> defaultUri() {
        return Optional.of(DEFAULT_URI);
    }

    @Override
    public boolean isSupported(FetchRequest request) {
        return request.uri()
                .map(uri -> "mica".equals(uri.getScheme()) && "viewRenderHistory".equals(uri.getHost()))
                .orElse(false);
    }

    @Override
    public Cursor fetch(FetchRequest request) {
        var uri = request.uri().orElseThrow(() -> new StoreException("mica://viewRenderHistory URI is required"));
        var params = new QueryParams(uri.getQuery());

        var after = params.getFirst("after").map(Instant::parse).orElse(Instant.now().minus(Duration.ofDays(31)));

        var query = dsl.select(MICA_VIEW_RENDER_HISTORY.ENTITY_ID,
                MICA_ENTITIES.NAME,
                MICA_VIEW_RENDER_HISTORY.RENDERED_AT,
                MICA_VIEW_RENDER_HISTORY.SELECT_TIME_MS,
                MICA_VIEW_RENDER_HISTORY.RENDER_TIME_MS,
                MICA_VIEW_RENDER_HISTORY.FETCHED_ENTITIES)
                .from(MICA_VIEW_RENDER_HISTORY)
                .leftOuterJoin(MICA_ENTITIES).on(MICA_VIEW_RENDER_HISTORY.ENTITY_ID.eq(MICA_ENTITIES.ID))
                .where(MICA_VIEW_RENDER_HISTORY.RENDERED_AT.greaterThan(after))
                .orderBy(MICA_VIEW_RENDER_HISTORY.RENDERED_AT.desc());

        return () -> query.fetch(ViewRenderHistoryEntityFetcher::toEntity).stream();
    }

    private static EntityLike toEntity(Record6<UUID, String, Instant, Long, Long, Integer> record) {
        return new EntityLike() {
            @Override
            public String name() {
                return "/view-render-history/%s/%s".formatted(record.value1(), record.value3().toString());
            }

            @Override
            public String kind() {
                return "/mica/view-render-history/entry/v1";
            }

            @Override
            public Map<String, JsonNode> data() {
                return Map.of(
                        "viewEntityId", TextNode.valueOf(record.value1().toString()),
                        "viewEntityName", TextNode.valueOf(record.value2()),
                        "renderedAt", TextNode.valueOf(record.value3().toString()),
                        "selectTimeMs", TextNode.valueOf(record.value4().toString()),
                        "renderTimeMs", TextNode.valueOf(record.value5().toString()),
                        "fetchedEntities", TextNode.valueOf(record.value6().toString()));
            }
        };
    }
}
