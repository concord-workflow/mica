package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.Entity;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.data.BuiltinSchemas;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.data.ViewProcessor;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.ws.rs.*;
import java.util.UUID;

import static ca.ibodrov.mica.server.exceptions.ApiException.ErrorKind.NO_DATA;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "View")
@Path("/api/mica/v1/view")
@Produces(APPLICATION_JSON)
public class ViewResource implements Resource {

    private final EntityStore entityStore;
    private final ViewProcessor viewProcessor;

    @Inject
    public ViewResource(EntityStore entityStore, ObjectMapper objectMapper) {
        this.entityStore = requireNonNull(entityStore);
        this.viewProcessor = new ViewProcessor(requireNonNull(objectMapper));
    }

    @GET
    @Path("render/{viewId}")
    @Operation(description = "Render a view", operationId = "render")
    public PartialEntity render(@PathParam("viewId") UUID viewId,
                                @QueryParam("limit") @DefaultValue("-1") int limit) {

        var view = entityStore.getById(viewId)
                .map(BuiltinSchemas::asView)
                .orElseThrow(() -> ApiException.notFound(NO_DATA, "View not found: " + viewId));

        var entities = entityStore.getAllByKind(view.selector().entityKind(), limit).stream()
                .map(Entity::asEntityLike);

        return viewProcessor.render(view, entities);
    }

    @POST
    @Path("/preview")
    @Consumes(APPLICATION_JSON)
    @Operation(description = "Preview a view", operationId = "preview")
    public PartialEntity preview(PreviewRequest request) {
        // TODO better validation
        if (request.view() == null) {
            throw ApiException.badRequest(NO_DATA, "Missing 'view' field");
        }

        var view = BuiltinSchemas.asView(request.view());

        var entities = entityStore.getAllByKind(view.selector().entityKind(), request.limit).stream()
                .map(Entity::asEntityLike);

        return viewProcessor.render(view, entities);
    }

    public record PreviewRequest(PartialEntity view, int limit) {
    }
}
