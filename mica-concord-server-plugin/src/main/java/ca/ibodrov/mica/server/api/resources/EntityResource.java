package ca.ibodrov.mica.server.api.resources;

import ca.ibodrov.mica.api.model.EntityId;
import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.server.api.ApiException;
import ca.ibodrov.mica.server.data.EntityController;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.RawValue;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record5;
import org.jooq.Record6;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ca.ibodrov.mica.db.jooq.Tables.MICA_ENTITIES;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.*;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.jooq.impl.DSL.noCondition;

@Tag(name = "Entity")
@Path("/api/mica/v1/entity")
@Produces(APPLICATION_JSON)
public class EntityResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(EntityResource.class);

    private final DSLContext dsl;
    private final EntityController controller;
    private final ObjectMapper yamlMapper;
    private final Validator validator;

    @Inject
    public EntityResource(@MicaDB DSLContext dsl,
                          EntityController controller,
                          ObjectMapper objectMapper,
                          Validator validator) {

        this.dsl = dsl;
        this.controller = controller;
        this.yamlMapper = objectMapper.copyWith(YAMLFactory.builder()
                .enable(MINIMIZE_QUOTES)
                .disable(SPLIT_LINES)
                .disable(WRITE_DOC_START_MARKER)
                .enable(LITERAL_BLOCK_STYLE)
                .build());
        this.validator = validator;
    }

    @GET
    @Operation(description = "List known entities", operationId = "listEntities")
    public EntityList listEntities(@Nullable @QueryParam("search") String search) {
        var searchCondition = search != null ? MICA_ENTITIES.NAME.containsIgnoreCase(search) : noCondition();
        var data = dsl.select(MICA_ENTITIES.ID,
                MICA_ENTITIES.NAME,
                MICA_ENTITIES.KIND,
                MICA_ENTITIES.CREATED_AT,
                MICA_ENTITIES.UPDATED_AT)
                .from(MICA_ENTITIES)
                .where(searchCondition)
                .fetch(EntityResource::convertToEntityEntry);
        return new EntityList(data);
    }

    @GET
    @Path("{id}")
    @Operation(description = "Get entity by ID", operationId = "getEntityById")
    public EntityWithData getEntityById(@PathParam("id") UUID entityId) {
        return dsl.select(MICA_ENTITIES.ID,
                MICA_ENTITIES.NAME,
                MICA_ENTITIES.KIND,
                MICA_ENTITIES.CREATED_AT,
                MICA_ENTITIES.UPDATED_AT,
                MICA_ENTITIES.DATA)
                .from(MICA_ENTITIES)
                .where(MICA_ENTITIES.ID.eq(entityId))
                .fetchOptional(EntityResource::convertToEntityEntryWithData)
                .orElseThrow(() -> ApiException.notFound("Entity not found: " + entityId));
    }

    @GET
    @Path("{id}/yaml")
    @Operation(description = "Get entity by ID in YAML format", operationId = "getEntityAsYamlString")
    public Response getEntityAsYamlString(@PathParam("id") UUID entityId) {
        var entity = getEntityById(entityId);
        try {
            var string = yamlMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(convertYamlRepresentableForm(entity));
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
            throw ApiException.badRequest("Error parsing YAML: " + e.getMessage());
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertYamlRepresentableForm(EntityWithData entity) {
        var map = yamlMapper.convertValue(entity, Map.class);
        var rawData = (String) ((RawValue) map.get("data")).rawValue();
        try {
            var data = yamlMapper.readValue(rawData, JsonNode.class);
            map.put("data", data);
        } catch (IOException e) {
            throw ApiException.internalError(e.getMessage());
        }
        return map;
    }

    private static EntityEntry convertToEntityEntry(Record5<UUID, String, String, OffsetDateTime, OffsetDateTime> record) {
        var id = new EntityId(record.value1());
        return new EntityEntry(id, record.value2(), record.value3(), record.value4(), record.value5());
    }

    private static EntityWithData convertToEntityEntryWithData(Record6<UUID, String, String, OffsetDateTime, OffsetDateTime, JSONB> record) {
        var id = new EntityId(record.value1());
        return new EntityWithData(id, record.value2(), record.value3(), record.value4(), record.value5(),
                record.value6().data());
    }

    public record EntityEntry(EntityId id,
            String name,
            String kind,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public EntityVersion toVersion() {
            return new EntityVersion(id, updatedAt);
        }
    }

    public record EntityWithData(EntityId id, String name, String kind, OffsetDateTime createdAt,
            OffsetDateTime updatedAt, @JsonRawValue String data) {
    }

    public record EntityList(List<EntityEntry> data) {
    }
}
