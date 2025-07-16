package ca.ibodrov.mica.server.ui;

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
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import org.jooq.DSLContext;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_ENTITIES;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.jooq.impl.DSL.*;

@Path("/api/mica/ui/entityList")
@Produces(APPLICATION_JSON)
public class EntityListResource implements Resource {

    private static final int LIST_LIMIT = 500;
    private static final int SEARCH_LIMIT = 100;

    private final DSLContext dsl;

    @Inject
    public EntityListResource(@MicaDB DSLContext dsl) {
        this.dsl = requireNonNull(dsl);
    }

    @GET
    @Path("canBeDeleted")
    public CanBeDeletedResponse canBeDeleted(@NotEmpty @QueryParam("entityId") EntityId entityId) {
        var record = dsl.select(MICA_ENTITIES.NAME, MICA_ENTITIES.DELETED_AT)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.ID.eq(entityId.id()))
                .fetchOptional()
                .orElseThrow(() -> ApiException.notFound("Entity ID not found: " + entityId.toExternalForm()));

        if (record.component1().startsWith("/mica/")) {
            // do not delete "system" entities
            return new CanBeDeletedResponse(false, Optional.of("System entities cannot be deleted."));
        }

        // check if already "deleted"
        if (record.component2() != null) {
            return new CanBeDeletedResponse(false, Optional.of("Already deleted"));
        }

        return new CanBeDeletedResponse(true, Optional.empty());
    }

    @GET
    @WithTimer
    public ListResponse list(@NotEmpty @QueryParam("path") String path,
                             @Nullable @QueryParam("entityKind") String entityKind,
                             @Nullable @QueryParam("search") String search,
                             @QueryParam("deleted") @DefaultValue("false") boolean deleted) {

        List<Entry> data;
        if (search == null || search.isBlank()) {
            assert path != null;
            path = path.endsWith("/") ? path : path + "/";
            data = list(path, entityKind, deleted);
        } else {
            data = search(entityKind, deleted, search);
        }

        return new ListResponse(data);
    }

    private List<Entry> list(String path, String entityKind, boolean deleted) {
        var pathLength = path.length();

        var deletedCondition = deleted ? MICA_ENTITIES.DELETED_AT.isNotNull() : MICA_ENTITIES.DELETED_AT.isNull();
        var entityKindCondition = entityKind != null && !entityKind.isBlank() ? MICA_ENTITIES.KIND.eq(entityKind)
                : noCondition();

        var filesQuery = dsl.select(
                substring(MICA_ENTITIES.NAME, val(pathLength + 1), length(MICA_ENTITIES.NAME).minus(pathLength))
                        .as("name"),
                MICA_ENTITIES.ID,
                MICA_ENTITIES.NAME.as("entityName"),
                MICA_ENTITIES.KIND.as("entityKind"),
                MICA_ENTITIES.DELETED_AT)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.NAME.like(path + "%")
                        .and(MICA_ENTITIES.NAME.notLike(path + "%/%"))
                        .and(entityKindCondition)
                        .and(deletedCondition))
                .limit(LIST_LIMIT);

        var files = filesQuery.stream()
                .map(r -> new Entry(
                        Type.FILE,
                        r.get("name", String.class),
                        Optional.of(r.get(MICA_ENTITIES.ID)).map(EntityId::new),
                        Optional.of(r.get("entityName", String.class)),
                        Optional.of(r.get("entityKind", String.class)),
                        Optional.ofNullable(r.get(MICA_ENTITIES.DELETED_AT))));

        var foldersQuery = dsl.selectDistinct(
                substring(MICA_ENTITIES.NAME, val(pathLength + 1),
                        position(substring(MICA_ENTITIES.NAME, val(pathLength + 1)), val("/")).minus(1))
                        .as("folderName"))
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.NAME.like(path + "%/%")
                        .and(entityKindCondition)
                        .and(deletedCondition))
                .limit(LIST_LIMIT);

        var folders = foldersQuery.stream()
                .map(r -> new Entry(
                        Type.FOLDER,
                        r.get("folderName", String.class),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()));

        return Stream.concat(folders, files).toList();
    }

    private List<Entry> search(String entityKind, boolean deleted, String search) {
        var deletedCondition = deleted ? MICA_ENTITIES.DELETED_AT.isNotNull() : MICA_ENTITIES.DELETED_AT.isNull();
        var entityKindCondition = entityKind != null && !entityKind.isBlank() ? MICA_ENTITIES.KIND.eq(entityKind)
                : noCondition();
        var searchCondition = MICA_ENTITIES.NAME.containsIgnoreCase(search);

        var filesQuery = dsl.select(
                MICA_ENTITIES.NAME,
                MICA_ENTITIES.ID,
                MICA_ENTITIES.NAME.as("entityName"),
                MICA_ENTITIES.KIND.as("entityKind"),
                MICA_ENTITIES.DELETED_AT)
                .from(MICA_ENTITIES)
                .where(entityKindCondition
                        .and(deletedCondition)
                        .and(searchCondition))
                .limit(SEARCH_LIMIT);

        return filesQuery.stream()
                .map(r -> new Entry(
                        Type.FILE,
                        r.get("name", String.class),
                        Optional.of(r.get(MICA_ENTITIES.ID)).map(EntityId::new),
                        Optional.of(r.get("entityName", String.class)),
                        Optional.of(r.get("entityKind", String.class)),
                        Optional.ofNullable(r.get(MICA_ENTITIES.DELETED_AT))))
                .toList();
    }

    public record CanBeDeletedResponse(boolean canBeDeleted, Optional<String> whyNot) {
    }

    public enum Type {
        FOLDER,
        FILE
    }

    @JsonInclude(Include.NON_ABSENT)
    public record Entry(Type type, String name, Optional<EntityId> entityId, Optional<String> entityName,
            Optional<String> entityKind, Optional<Instant> deletedAt) {
    }

    public record ListResponse(List<Entry> data) {
    }
}
