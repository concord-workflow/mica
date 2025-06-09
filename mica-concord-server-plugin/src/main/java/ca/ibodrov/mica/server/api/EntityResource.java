package ca.ibodrov.mica.server.api;

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

import ca.ibodrov.mica.api.model.*;
import ca.ibodrov.mica.server.YamlMapper;
import ca.ibodrov.mica.server.data.EntityController;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.data.EntityStore.ListEntitiesRequest;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jooq.Delete;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.UUID;

import static ca.ibodrov.mica.server.api.ApiUtils.nonBlank;
import static ca.ibodrov.mica.server.api.ApiUtils.parseIsoAsInstant;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.HttpHeaders.CONTENT_DISPOSITION;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "Entity")
@Path("/api/mica/v1/entity")
@Produces(APPLICATION_JSON)
public class EntityResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(EntityResource.class);

    private final EntityStore entityStore;
    private final EntityController entityController;
    private final YamlMapper yamlMapper;

    @Inject
    public EntityResource(EntityStore entityStore,
                          EntityController entityController,
                          ObjectMapper objectMapper) {

        this.entityStore = requireNonNull(entityStore);
        this.entityController = requireNonNull(entityController);
        this.yamlMapper = new YamlMapper(objectMapper);
    }

    @GET
    @Operation(summary = "List known entities", operationId = "listEntities")
    public EntityList listEntities(@Nullable @QueryParam("search") String search,
                                   @Nullable @QueryParam("entityNameStartsWith") String entityNameStartsWith,
                                   @Nullable @QueryParam("entityName") String entityName,
                                   @Nullable @QueryParam("entityKind") String entityKind,
                                   @Nullable @QueryParam("orderBy") OrderBy orderBy) {

        // TODO validate entityName and entityKind, use @ValidName
        var request = new ListEntitiesRequest(nonBlank(search),
                nonBlank(entityNameStartsWith),
                nonBlank(entityName),
                nonBlank(entityKind),
                orderBy);
        var data = entityStore.search(request);
        return new EntityList(data);
    }

    @GET
    @Path("{id}")
    @Operation(summary = "Get entity by ID", operationId = "getEntityById")
    public Entity getEntityById(@PathParam("id") EntityId entityId,
                                @Nullable @QueryParam("updatedAt") String updatedAtString) {

        var updatedAt = parseIsoAsInstant(updatedAtString).orElse(null);
        return entityStore.getById(entityId, updatedAt)
                .orElseThrow(() -> ApiException.notFound("Entity not found: " + entityId));
    }

    @GET
    @Path("{id}/yaml")
    @Operation(summary = "Get entity by ID in YAML format", operationId = "getEntityAsYamlString")
    public Response getEntityAsYamlString(@PathParam("id") EntityId entityId,
                                          @Nullable @QueryParam("updatedAt") String updatedAt) {

        var entity = getEntityById(entityId, updatedAt);
        try {
            var string = yamlMapper.prettyPrint(entity);
            return Response.ok(string, "text/yaml").build();
        } catch (IOException e) {
            log.warn("YAML serialization error: {}", e.getMessage(), e);
            throw ApiException.internalError(e.getMessage());
        }
    }

    @GET
    @Path("{id}/doc")
    @Operation(summary = "Return the original unparsed YAML (or JSON) document for the entity", operationId = "getEntityDoc")
    @Produces("text/yaml")
    public Response getEntityDoc(@PathParam("id") EntityId entityId,
                                 @Nullable @QueryParam("updatedAt") String updatedAtString) {

        var versionedDoc = parseIsoAsInstant(updatedAtString)
                .map(updatedAt -> new EntityVersion(entityId, updatedAt))
                .flatMap(entityStore::getEntityDoc);
        if (versionedDoc.isPresent()) {
            return Response.ok(versionedDoc.get()).build();
        }

        var latestDoc = entityStore.getLatestEntityDoc(entityId);
        if (latestDoc.isPresent()) {
            return Response.ok(latestDoc.get()).build();
        }

        // render the saved entity as YAML if the original doc is missing
        var entity = entityStore.getById(entityId)
                .orElseThrow(() -> ApiException.notFound("Entity not found: " + entityId));
        try {
            var renderedDoc = yamlMapper.prettyPrint(entity);
            return Response.ok(renderedDoc, "text/yaml").build();
        } catch (IOException e) {
            log.warn("YAML serialization error: {}", e.getMessage(), e);
            throw ApiException.internalError(e.getMessage());
        }
    }

    @GET
    @Path("{id}/download")
    @Operation(summary = "Downloads the original unparsed YAML (or JSON) document for the entity", operationId = "downloadEntityDoc")
    @Produces("text/yaml")
    public Response downloadEntityDoc(@PathParam("id") EntityId entityId,
                                      @Nullable @QueryParam("updatedAt") String updatedAtString) {
        return Response.fromResponse(getEntityDoc(entityId, updatedAtString))
                .header(CONTENT_DISPOSITION, "attachment; filename=\"%s.yaml\"".formatted(entityId.toExternalForm()))
                .build();
    }

    @DELETE
    @Path("{id}")
    @Operation(summary = "Delete an existing entity by its ID", operationId = "deleteById")
    public DeletedEntityVersion deleteById(@Context UserPrincipal session, @PathParam("id") UUID entityId) {
        return entityController.deleteById(session, new EntityId(entityId))
                .orElseThrow(() -> ApiException.notFound("Entity not found: " + entityId));
    }
}
