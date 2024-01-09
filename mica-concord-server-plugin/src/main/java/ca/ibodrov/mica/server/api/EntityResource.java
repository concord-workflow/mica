package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.*;
import ca.ibodrov.mica.server.YamlMapper;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.data.EntityStore.ListEntitiesRequest;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.DateTimeUtils;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;

@Tag(name = "Entity")
@Path("/api/mica/v1/entity")
@Produces(APPLICATION_JSON)
public class EntityResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(EntityResource.class);

    private final EntityStore entityStore;
    private final YamlMapper yamlMapper;

    @Inject
    public EntityResource(EntityStore entityStore, ObjectMapper objectMapper) {
        this.entityStore = requireNonNull(entityStore);
        this.yamlMapper = new YamlMapper(objectMapper);
    }

    @GET
    @Operation(summary = "List known entities", operationId = "listEntities")
    public EntityList listEntities(@Nullable @QueryParam("search") String search,
                                   @Nullable @QueryParam("entityNameStartsWith") String entityNameStartsWith,
                                   @Nullable @QueryParam("entityName") String entityName,
                                   @Nullable @QueryParam("entityKind") String entityKind,
                                   @Nullable @QueryParam("orderBy") OrderBy orderBy,
                                   @QueryParam("limit") @DefaultValue("100") int limit) {

        // TODO validate entityName and entityKind, use @ValidName
        var request = new ListEntitiesRequest(nonBlank(search),
                nonBlank(entityNameStartsWith),
                nonBlank(entityName),
                nonBlank(entityKind),
                orderBy,
                limit);
        var data = entityStore.search(request);
        return new EntityList(limit, data);
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
    @Produces(APPLICATION_OCTET_STREAM)
    public Response getEntityDoc(@PathParam("id") EntityId entityId,
                                 @Nullable @QueryParam("updatedAt") String updatedAtString) {

        var updatedAt = parseIsoAsInstant(updatedAtString).orElse(null);
        var doc = entityStore.getEntityDocById(entityId, updatedAt)
                .orElseGet(() -> {
                    var entity = entityStore.getById(entityId, updatedAt)
                            .orElseThrow(() -> ApiException.notFound("Entity not found: " + entityId));

                    try {
                        return yamlMapper.prettyPrintAsBytes(entity);
                    } catch (IOException e) {
                        log.warn("YAML serialization error: {}", e.getMessage(), e);
                        throw ApiException.internalError(e.getMessage());
                    }
                });
        return Response.ok(doc).build();
    }

    @DELETE
    @Path("{id}")
    @Operation(summary = "Delete an existing entity by its ID", operationId = "deleteById")
    public EntityVersion deleteById(@PathParam("id") UUID entityId) {
        return entityStore.deleteById(new EntityId(entityId))
                .orElseThrow(() -> ApiException.notFound("Entity not found: " + entityId));
    }

    private static String nonBlank(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s;
    }

    private static Optional<Instant> parseIsoAsInstant(String s) {
        return Optional.ofNullable(nonBlank(s))
                .map(DateTimeUtils::fromIsoString)
                .map(OffsetDateTime::toInstant);
    }
}
