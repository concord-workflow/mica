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

import ca.ibodrov.mica.api.kinds.MicaDashboardV1;
import ca.ibodrov.mica.api.kinds.MicaDashboardV1.TableLayout;
import ca.ibodrov.mica.api.model.EntityId;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.api.model.PreviewViewRequest;
import ca.ibodrov.mica.api.model.RenderViewRequest;
import ca.ibodrov.mica.server.data.BuiltinSchemas;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.data.JsonPathEvaluator;
import ca.ibodrov.mica.server.data.ViewController;
import ca.ibodrov.mica.server.data.ViewRenderer.RenderOverrides;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import io.swagger.v3.oas.annotations.Operation;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/api/mica/v1/dashboard")
@Produces(APPLICATION_JSON)
public class DashboardResource implements Resource {

    private final EntityStore entityStore;
    private final ViewController viewController;
    private final JsonPathEvaluator jsonPathEvaluator;

    @Inject
    public DashboardResource(EntityStore entityStore,
                             ViewController viewController,
                             JsonPathEvaluator jsonPathEvaluator) {
        this.entityStore = requireNonNull(entityStore);
        this.viewController = requireNonNull(viewController);
        this.jsonPathEvaluator = requireNonNull(jsonPathEvaluator);
    }

    @GET
    @Path("render/{entityId}")
    @Operation(summary = "Render a dashboard", operationId = "renderDashboard")
    @Validate
    @WithTimer
    public DashboardRenderResponse render(@PathParam("entityId") EntityId entityId) {
        var dashboard = entityStore.getById(entityId)
                .map(BuiltinSchemas::asMicaDashboardV1)
                .orElseThrow(() -> new IllegalArgumentException("Dashboard not found (entityId=" + entityId + ")"));
        return render(dashboard);
    }

    @POST
    @Path("preview")
    @Operation(summary = "Preview a dashboard", operationId = "previewDashboard")
    @Validate
    @WithTimer
    public DashboardRenderResponse preview(@Valid DashboardPreviewRequest request) {
        var dashboard = BuiltinSchemas.asMicaDashboardV1(request.dashboard());
        return preview(dashboard);
    }

    private DashboardRenderResponse render(MicaDashboardV1 dashboard) {
        var table = assertTable(dashboard);

        var request = toRenderViewRequest(dashboard);
        var renderedView = viewController.getCachedOrRender(request, RenderOverrides.none());

        var data = renderedView.data().stream()
                .map(row -> parseRow(row, table))
                .toList();

        return new DashboardRenderResponse(dashboard, data);
    }

    private DashboardRenderResponse preview(MicaDashboardV1 dashboard) {
        var table = assertTable(dashboard);

        var request = toPreviewViewRequest(dashboard);
        var renderedView = viewController.preview(request);

        ArrayList<List<JsonNode>> result;
        var data = renderedView.data().get("data");
        if (data != null && data.isArray()) {
            result = new ArrayList<>(data.size());
            data.forEach(row -> result.add(parseRow(row, table)));
        } else {
            throw new IllegalArgumentException("Invalid view data"); // TODO detailed message
        }

        return new DashboardRenderResponse(dashboard, result);
    }

    private List<JsonNode> parseRow(JsonNode row, TableLayout table) {
        return table.columns().stream()
                .map(col -> jsonPathEvaluator.applyInApiCall(row, col.jsonPath())
                        .orElse(NullNode.getInstance()))
                .toList();
    }

    private static TableLayout assertTable(MicaDashboardV1 dashboard) {
        return dashboard.table()
                .orElseThrow(() -> new IllegalArgumentException("Non-table layouts are not supported yet."));
    }

    private static RenderViewRequest toRenderViewRequest(MicaDashboardV1 dashboard) {
        var viewRef = dashboard.view();
        return RenderViewRequest.parameterized(viewRef.name(), viewRef.parameters().orElse(null));
    }

    private PreviewViewRequest toPreviewViewRequest(MicaDashboardV1 dashboard) {
        var viewRef = dashboard.view();
        var viewEntity = entityStore.getByName(viewRef.name())
                .orElseThrow(() -> new IllegalArgumentException("Invalid viewRef. View not found: " + viewRef.name()))
                .asPartialEntity();
        return new PreviewViewRequest(viewEntity, viewRef.parameters());
    }

    public record DashboardPreviewRequest(@NotNull PartialEntity dashboard) {
    }

    public record DashboardRenderResponse(MicaDashboardV1 dashboard, List<List<JsonNode>> data) {
    }
}
