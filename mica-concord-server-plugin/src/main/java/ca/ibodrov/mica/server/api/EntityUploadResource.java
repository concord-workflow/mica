package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.YamlMapper;
import ca.ibodrov.mica.server.data.EntityController;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "Entity Upload")
@Path("/api/mica/v1/upload")
@Produces(APPLICATION_JSON)
public class EntityUploadResource implements Resource {

    private final EntityController controller;
    private final Validator validator;
    private final YamlMapper yamlMapper;

    @Inject
    public EntityUploadResource(EntityController controller,
                                Validator validator,
                                ObjectMapper objectMapper) {

        this.controller = requireNonNull(controller);
        this.validator = requireNonNull(validator);
        this.yamlMapper = new YamlMapper(objectMapper);
    }

    @PUT
    @Path("yaml")
    @Consumes("*/yaml")
    @Operation(summary = "Upload an entity in YAML format", operationId = "putYaml")
    public EntityVersion putYaml(@Context UserPrincipal session,
                                 InputStream in,
                                 @QueryParam("overwrite") @DefaultValue("false") boolean overwrite) {

        // assume the name is present in the document
        return putPartialYaml(session, null, null, false, overwrite, in);
    }

    @PUT
    @Path("partialYaml")
    @Consumes("*/yaml")
    @Operation(summary = "Upload a partial entity in YAML format", description = "Upload a (possibly) partial entity in YAML format with 'name' or 'kind' overrides", operationId = "putPartialYaml")
    public EntityVersion putPartialYaml(@Context UserPrincipal session,
                                        @Nullable @QueryParam("entityName") String entityName,
                                        @Nullable @QueryParam("entityKind") String entityKind,
                                        @Parameter(description = "Replace any entity with the same name") @QueryParam("replace") @DefaultValue("false") boolean replace,
                                        @Parameter(description = "Overwrite any other changes to the entity") @QueryParam("overwrite") @DefaultValue("false") boolean overwrite,
                                        InputStream in) {
        byte[] doc;
        try {
            doc = in.readAllBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Error reading the input: " + e.getMessage());
        }

        // TODO validate entityName
        PartialEntity entity;
        try {
            var object = yamlMapper.readValue(new ByteArrayInputStream(doc), ObjectNode.class);
            if (entityName != null && !entityName.isBlank()) {
                object = object.put("name", entityName);
            }
            if (entityKind != null && !entityKind.isBlank()) {
                object = object.put("kind", entityKind);
            }
            entity = yamlMapper.convertValue(object, PartialEntity.class);
        } catch (IOException e) {
            throw ApiException.badRequest("Error parsing YAML: " + e.getMessage());
        }
        assertValid(entity);
        // TODO single transaction
        if (replace) {
            controller.deleteIfExists(session, entity.name());
        }
        return controller.createOrUpdate(session, entity, doc, overwrite);
    }

    private void assertValid(PartialEntity entity) {
        var violations = validator.validate(entity);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
