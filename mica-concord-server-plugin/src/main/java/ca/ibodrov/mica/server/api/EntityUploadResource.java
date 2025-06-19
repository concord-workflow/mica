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

import ca.ibodrov.mica.api.model.EntityVersion;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.YamlMapper;
import ca.ibodrov.mica.server.data.EntityController;
import ca.ibodrov.mica.server.data.EntityController.UpdateIf;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.annotation.Nullable;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import java.io.IOException;

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
    @RolesAllowed({ Roles.ADMIN, Roles.SYSTEM_WRITER })
    @WithTimer
    public EntityVersion putYaml(@Context UserPrincipal session,
                                 @QueryParam("overwrite") @DefaultValue("false") boolean overwrite,
                                 String doc) {

        // assume the name is present in the document
        return putPartialYaml(session, null, null, false, overwrite, null, doc);
    }

    @PUT
    @Path("partialYaml")
    @Consumes("*/yaml")
    @Operation(summary = "Upload a partial entity in YAML format", description = "Upload a (possibly) partial entity in YAML format with 'name' or 'kind' overrides", operationId = "putPartialYaml")
    @RolesAllowed({ Roles.ADMIN, Roles.SYSTEM_WRITER })
    @WithTimer
    public EntityVersion putPartialYaml(@Context UserPrincipal session,
                                        @Nullable @QueryParam("entityName") String entityName,
                                        @Nullable @QueryParam("entityKind") String entityKind,
                                        @Parameter(description = "Replace any entity with the same name") @QueryParam("replace") @DefaultValue("false") boolean replace,
                                        @Parameter(description = "Overwrite any other changes to the entity") @QueryParam("overwrite") @DefaultValue("false") boolean overwrite,
                                        @Parameter(description = "Conditional update ('structuralDiff' -- update only if there are structural differences, i.e. ignore comments, formatting or updatedAt field)") @QueryParam("updateIf") String updateIfMode,
                                        String doc) {

        var updateIf = UpdateIf.parse(updateIfMode);

        // TODO validate entityName
        PartialEntity entity;
        try {
            var object = yamlMapper.readValue(doc, ObjectNode.class);
            if (entityName != null && !entityName.isBlank()) {
                object = object.put("name", entityName);
            }
            if (entityKind != null && !entityKind.isBlank()) {
                object = object.put("kind", entityKind);
            }
            entity = yamlMapper.convertValue(object, PartialEntity.class);
        } catch (IllegalArgumentException | IOException e) {
            if (e.getCause() instanceof InvalidFormatException ex) {
                throw ApiException.badRequest("Error parsing YAML: " + ex.getMessage());
            }
            throw ApiException.badRequest("Error parsing YAML: " + e.getMessage());
        }

        var violations = validator.validate(entity);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException("Invalid entity", violations);
        }

        return controller.put(session, entity, doc, overwrite, replace, updateIf);
    }
}
