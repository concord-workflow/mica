package ca.ibodrov.mica.server.data;

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
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record6;
import org.jooq.Record7;

import javax.inject.Inject;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_ENTITIES;
import static java.util.Objects.requireNonNull;

public class InternalEntityFetcher implements EntityFetcher {

    private static final URI DEFAULT_URI = URI.create("mica://internal");

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    @Inject
    public InternalEntityFetcher(@MicaDB DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = requireNonNull(dsl);
        this.objectMapper = requireNonNull(objectMapper);
    }

    @Override
    public Optional<URI> defaultUri() {
        return Optional.of(DEFAULT_URI);
    }

    @Override
    public boolean isSupported(FetchRequest request) {
        // the fetcher supports requests both with and without URIs
        return request.uri()
                .map(uri -> "mica".equals(uri.getScheme()) && "internal".equals(uri.getHost()))
                .orElse(true);
    }

    @Override
    public Cursor fetch(FetchRequest request) {
        // this method should NOT return "deleted" entities

        var kind = request.kind().orElseThrow(() -> new StoreException("selector.entityKind is required"));

        var step = dsl.select(MICA_ENTITIES.ID,
                MICA_ENTITIES.NAME,
                MICA_ENTITIES.KIND,
                MICA_ENTITIES.CREATED_AT,
                MICA_ENTITIES.UPDATED_AT,
                MICA_ENTITIES.DELETED_AT, // should be null
                MICA_ENTITIES.DATA)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.DELETED_AT.isNull()
                        .and(MICA_ENTITIES.KIND.likeRegex(kind)));

        return () -> step.fetch(this::toEntity).stream();
    }

    private EntityLike toEntity(Record7<UUID, String, String, Instant, Instant, Instant, JSONB> record) {
        return EntityStore.toEntity(objectMapper, record);
    }
}
