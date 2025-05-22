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
import ca.ibodrov.mica.api.model.RenderRequest;
import ca.ibodrov.mica.server.data.BuiltinSchemas;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.data.JsonPathEvaluator;
import ca.ibodrov.mica.server.data.ViewController;
import ca.ibodrov.mica.server.data.ViewRenderer.RenderOverrides;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.walmartlabs.concord.server.sdk.rest.Resource;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
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
    @Path("{entityId}")
    public DashboardRenderResponse render(@PathParam("entityId") EntityId entityId) {
        var dashboardEntity = entityStore.getById(entityId)
                .orElseThrow(() -> new IllegalArgumentException("Dashboard not found (entityId=" + entityId + ")"));
        var dashboard = BuiltinSchemas.asMicaDashboardV1(dashboardEntity);

        var table = dashboard.table()
                .orElseThrow(() -> new IllegalArgumentException("Non-table layouts are not supported yet."));

        var request = toRenderRequest(dashboard);
        var renderedView = viewController.getCachedOrRender(request, RenderOverrides.none());

        var data = renderedView.data().stream()
                .map(row -> parseRow(dashboardEntity.name(), row, table))
                .toList();
        return new DashboardRenderResponse(dashboard, data);
    }

    private List<JsonNode> parseRow(String entityName, JsonNode row, TableLayout table) {
        return table.columns().stream()
                .map(col -> jsonPathEvaluator.applyInApiCall(entityName, row, col.jsonPath())
                        .orElse(NullNode.getInstance()))
                .toList();
    }

    public record DashboardRenderResponse(MicaDashboardV1 dashboard, List<List<JsonNode>> data) {
    }

    private static RenderRequest toRenderRequest(MicaDashboardV1 dashboard) {
        var viewRef = dashboard.view();
        return RenderRequest.parameterized(viewRef.name(), viewRef.parameters().orElse(null));
    }
}
