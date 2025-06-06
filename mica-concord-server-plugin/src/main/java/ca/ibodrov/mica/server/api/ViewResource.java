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

import ca.ibodrov.mica.api.model.EntityId;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.api.model.PreviewViewRequest;
import ca.ibodrov.mica.api.model.RenderViewRequest;
import ca.ibodrov.mica.server.data.ViewController;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Tag(name = "View")
@Path("/api/mica/v1/view")
@Produces(APPLICATION_JSON)
public class ViewResource implements Resource {

    private final ViewController controller;

    @Inject
    public ViewResource(ViewController controller) {
        this.controller = requireNonNull(controller);
    }

    @POST
    @Path("render")
    @Consumes(APPLICATION_JSON)
    @Operation(summary = "Render a view", operationId = "render")
    @Validate
    @WithTimer
    public PartialEntity render(@Valid RenderViewRequest request) {
        return controller.getCachedOrRenderAsEntity(request);
    }

    @POST
    @Path("renderProperties")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @Operation(summary = "Render a view into a .properties file", operationId = "renderProperties")
    @Validate
    @WithTimer
    public String renderProperties(@Valid RenderViewRequest request) {
        return controller.getCachedOrRenderAsProperties(request);
    }

    @GET
    @Path("render/{viewId}")
    @Operation(summary = "Render a simple view (without parameters)", operationId = "renderSimple")
    @Validate
    @WithTimer
    public PartialEntity renderSimple(@PathParam("viewId") EntityId viewId) {
        var request = new RenderViewRequest(Optional.of(viewId), Optional.empty(), Optional.empty());
        return controller.getCachedOrRenderAsEntity(request);
    }

    @POST
    @Path("/preview")
    @Consumes(APPLICATION_JSON)
    @Operation(summary = "Preview a view", operationId = "preview")
    @Validate
    @WithTimer
    public PartialEntity preview(@Valid PreviewViewRequest request) {
        return controller.preview(request);
    }

    @POST
    @Path("/materialize")
    @Consumes(APPLICATION_JSON)
    @Operation(summary = "Materialize a view", description = "Render a view and save the result as entities", operationId = "materialize")
    @Validate
    @WithTimer
    public PartialEntity materialize(@Valid RenderViewRequest request) {
        return controller.materialize(request);
    }
}
