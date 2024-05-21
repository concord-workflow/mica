package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.EntityId;
import ca.ibodrov.mica.server.data.EntityHistoryController;
import ca.ibodrov.mica.server.data.EntityHistoryController.EntityHistoryEntry;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;

import static ca.ibodrov.mica.server.api.ApiUtils.parseIsoAsInstant;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;

@Tag(name = "Entity History")
@Path("/api/mica/v1/history")
@Produces(APPLICATION_JSON)
public class EntityHistoryResource implements Resource {

    private final EntityHistoryController controller;

    @Inject
    public EntityHistoryResource(EntityHistoryController controller) {
        this.controller = requireNonNull(controller);
    }

    @GET
    @Path("{entityId}")
    @Operation(summary = "Return last N history entries for a given entity", operationId = "listHistory")
    public EntityHistory listHistory(@PathParam("entityId") EntityId entityId,
                                     @QueryParam("limit") @DefaultValue("10") int limit) {
        var data = controller.list(entityId, limit);
        return new EntityHistory(data);
    }

    @GET
    @Path("{entityId}/{updatedAt}/doc")
    @Operation(summary = "Return the original unparsed YAML (or JSON) document for the history entry", operationId = "getHistoryDoc")
    @Produces(APPLICATION_OCTET_STREAM)
    public Response getHistoryDoc(@PathParam("entityId") EntityId entityId,
                                  @PathParam("updatedAt") String updatedAtString) {
        var updatedAt = parseIsoAsInstant(updatedAtString)
                .orElseThrow(() -> ApiException.badRequest("Invalid 'updatedAt' value"));
        return controller.getHistoryDoc(entityId, updatedAt)
                .map(Response::ok)
                .orElseThrow(() -> ApiException.notFound("Document not found"))
                .build();
    }

    public record EntityHistory(List<EntityHistoryEntry> data) {
    }
}
