package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.Entity;
import ca.ibodrov.mica.api.model.EntityList;
import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.data.EntityController;
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
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static ca.ibodrov.mica.server.exceptions.ApiException.ErrorKind.BAD_DATA;
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
    private final EntityController controller;
    private final Validator validator;
    private final ObjectMapper yamlMapper;

    @Inject
    public EntityResource(EntityStore entityStore,
                          EntityController controller,
                          ObjectMapper objectMapper,
                          Validator validator) {

        this.entityStore = requireNonNull(entityStore);
        this.controller = requireNonNull(controller);
        this.validator = requireNonNull(validator);
        this.yamlMapper = objectMapper.copyWith(YAMLFactory.builder()
                .enable(MINIMIZE_QUOTES)
                .disable(SPLIT_LINES)
                .disable(WRITE_DOC_START_MARKER)
                .enable(LITERAL_BLOCK_STYLE)
                .build());
    }

    @GET
    @Operation(description = "List known entities", operationId = "listEntities")
    public EntityList listEntities(@Nullable @QueryParam("search") String search,
                                   @Nullable @QueryParam("entityName") String entityName) {
        var data = entityStore.search(nonBlank(search), nonBlank(entityName));
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

    @PUT
    @Consumes("*/yaml")
    @Operation(description = "Upload an entity in YAML format", operationId = "putYaml")
    public EntityVersion putYaml(InputStream in) {
        PartialEntity entity;
        try {
            entity = yamlMapper.readValue(in, PartialEntity.class);
        } catch (IOException e) {
            throw ApiException.badRequest(BAD_DATA, "Error parsing YAML: " + e.getMessage());
        }
        assertValid(entity);
        return controller.createOrUpdate(entity);
    }

    private void assertValid(PartialEntity entity) {
        var violations = validator.validate(entity);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private static String nonBlank(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s;
    }
}
