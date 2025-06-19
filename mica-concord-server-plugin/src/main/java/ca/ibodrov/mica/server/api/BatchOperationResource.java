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

import ca.ibodrov.mica.api.model.BatchOperation;
import ca.ibodrov.mica.api.model.BatchOperationRequest;
import ca.ibodrov.mica.api.model.BatchOperationResult;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.security.Roles;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "Batch Entity Operations")
@Path("/api/mica/v1/batch")
@Produces(APPLICATION_JSON)
public class BatchOperationResource implements Resource {

    private final EntityStore entityStore;

    @Inject
    public BatchOperationResource(EntityStore entityStore) {
        this.entityStore = requireNonNull(entityStore);
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Validate
    @RolesAllowed({ Roles.ADMIN, Roles.SYSTEM_WRITER })
    public BatchOperationResult apply(@Valid BatchOperationRequest request) {
        assert request.operation() != null;
        if (request.operation() != BatchOperation.DELETE) {
            throw ApiException.badRequest("Unsupported operation: " + request.operation());
        }

        var namePatterns = request.namePatterns()
                .orElseThrow(() -> ApiException.badRequest("Missing 'namePatterns'"));

        if (namePatterns.isEmpty()) {
            throw new IllegalArgumentException("Empty 'namePatterns'");
        }

        var invalidNamePatterns = namePatterns.stream().filter(BatchOperationResource::isNotValidRegex).toList();
        if (!invalidNamePatterns.isEmpty()) {
            throw ApiException.badRequest("Invalid 'namePatterns': " + String.join(", ", invalidNamePatterns));
        }

        var deletedEntities = entityStore.deleteByNamePatterns(namePatterns);
        return new BatchOperationResult(Optional.of(deletedEntities));
    }

    private static boolean isNotValidRegex(String pattern) {
        try {
            Pattern.compile(pattern);
            return false;
        } catch (PatternSyntaxException e) {
            return true;
        }
    }
}
