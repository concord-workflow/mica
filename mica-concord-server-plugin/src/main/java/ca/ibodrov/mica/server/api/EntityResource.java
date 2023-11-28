package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.Entity;
import ca.ibodrov.mica.api.model.EntityList;
import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.UUID;

import static ca.ibodrov.mica.server.exceptions.ApiException.ErrorKind.NO_DATA;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.*;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "Entity")
@Path("/api/mica/v1/entity")
@Produces(APPLICATION_JSON)
public class EntityResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(EntityResource.class);

    private final EntityStore entityStore;
    private final ObjectMapper yamlMapper;

    @Inject
    public EntityResource(EntityStore entityStore, ObjectMapper objectMapper) {
        this.entityStore = requireNonNull(entityStore);
        this.yamlMapper = objectMapper.copyWith(YAMLFactory.builder()
                .enable(MINIMIZE_QUOTES)
                .disable(SPLIT_LINES)
                .disable(WRITE_DOC_START_MARKER)
                .enable(LITERAL_BLOCK_STYLE)
                .build());
    }

    @GET
    @Operation(description = "List known entities", operationId = "listEntities")
    public EntityList listEntities(@QueryParam("search") String search,
                                   @Nullable @QueryParam("entityName") String entityName,
                                   @Nullable @QueryParam("entityKind") String entityKind) {
        // TODO validate entityName and entityKind
        var data = entityStore.search(nonBlank(search), nonBlank(entityName), nonBlank(entityKind));
        return new EntityList(data);
    }

    @GET
    @Path("{id}")
    @Operation(description = "Get entity by ID", operationId = "getEntityById")
    public Entity getEntityById(@PathParam("id") UUID entityId) {
        return entityStore.getById(entityId)
                .orElseThrow(() -> ApiException.notFound(NO_DATA, "Entity not found: " + entityId));
    }

    @GET
    @Path("{id}/yaml")
    @Operation(description = "Get entity by ID in YAML format", operationId = "getEntityAsYamlString")
    public Response getEntityAsYamlString(@PathParam("id") UUID entityId) {
        var entity = getEntityById(entityId);
        try {
            var string = yamlMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(entity);
            return Response.ok(string, "text/yaml").build();
        } catch (IOException e) {
            log.warn("YAML serialization error: {}", e.getMessage(), e);
            throw ApiException.internalError(e.getMessage());
        }
    }

    @DELETE
    @Path("{id}")
    @Operation(description = "Delete an existing entity by its ID", operationId = "deleteById")
    public EntityVersion deleteById(@PathParam("id") UUID entityId) {
        return entityStore.deleteById(entityId)
                .orElseThrow(() -> ApiException.notFound(NO_DATA, "Entity not found: " + entityId));
    }

    private static String nonBlank(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s;
    }
}
