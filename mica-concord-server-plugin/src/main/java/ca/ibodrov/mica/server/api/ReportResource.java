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

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.server.reports.ValidateAllReport;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "Report")
@Path("/api/mica/v1/report")
@Produces(APPLICATION_JSON)
public class ReportResource implements Resource {

    private final ValidateAllReport validateAll;

    @Inject
    public ReportResource(ValidateAllReport validateAll) {
        this.validateAll = requireNonNull(validateAll);
    }

    @POST
    @Path("validateAll")
    @Consumes(APPLICATION_JSON)
    @Operation(summary = "Validate all entities", operationId = "validateAll")
    public EntityLike validateAll(ValidateAllReport.Options options) {
        return validateAll.run(options);
    }
}
