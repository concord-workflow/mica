package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.kinds.MicaDashboardV1;
import ca.ibodrov.mica.api.model.EntityId;
import ca.ibodrov.mica.api.model.RenderRequest;
import ca.ibodrov.mica.server.data.BuiltinSchemas;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.data.ViewController;
import com.fasterxml.jackson.databind.JsonNode;
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

    @Inject
    public DashboardResource(EntityStore entityStore, ViewController viewController) {
        this.entityStore = requireNonNull(entityStore);
        this.viewController = requireNonNull(viewController);
    }

    @GET
    @Path("{entityId}")
    public DashboardRenderResponse render(@PathParam("entityId") EntityId entityId) {
        var dashboard = entityStore.getById(entityId)
                .map(BuiltinSchemas::asMicaDashboardV1)
                .orElseThrow(() -> new IllegalArgumentException("Dashboard not found (entityId=" + entityId + ")"));
        var request = toRenderRequest(dashboard);
        var renderedView = viewController.render(request);
        return new DashboardRenderResponse(dashboard, renderedView.data());
    }

    public record DashboardRenderResponse(MicaDashboardV1 dashboard, List<JsonNode> data) {
    }

    private static RenderRequest toRenderRequest(MicaDashboardV1 dashboard) {
        var viewRef = dashboard.view();
        return RenderRequest.parameterized(viewRef.name(), viewRef.parameters().orElse(null), -1);
    }
}
