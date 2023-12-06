package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.Entity;
import ca.ibodrov.mica.api.model.EntityId;
import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.data.BuiltinSchemas;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.data.ViewProcessor;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "View")
@Path("/api/mica/v1/view")
@Produces(APPLICATION_JSON)
public class ViewResource implements Resource {

    private final EntityStore entityStore;
    private final ObjectMapper objectMapper;
    private final ViewProcessor viewProcessor;

    @Inject
    public ViewResource(EntityStore entityStore, ObjectMapper objectMapper) {
        this.entityStore = requireNonNull(entityStore);
        this.objectMapper = requireNonNull(objectMapper);
        this.viewProcessor = new ViewProcessor(objectMapper);
    }

    @POST
    @Path("render")
    @Consumes(APPLICATION_JSON)
    @Operation(description = "Render a view", operationId = "render")
    public PartialEntity render(@Valid RenderRequest request) {
        var view = BuiltinSchemas.asView(objectMapper, assertViewEntity(request));
        var parameters = request.parameters().orElseGet(Map::of);

        var entities = entityStore.getAllByKind(view.selector().entityKind(), request.limit())
                .stream()
                .map(Entity::asEntityLike);

        return viewProcessor.render(view, parameters, entities);
    }

    @GET
    @Path("render/{viewId}")
    @Operation(description = "Render a simple view (without parameters)", operationId = "renderSimple")
    public PartialEntity renderSimple(@PathParam("viewId") EntityId viewId,
                                      @QueryParam("limit") @DefaultValue("-1") int limit) {

        return render(new RenderRequest(Optional.of(viewId), Optional.empty(), limit, Optional.empty()));
    }

    @POST
    @Path("/preview")
    @Consumes(APPLICATION_JSON)
    @Operation(description = "Preview a view", operationId = "preview")
    public PartialEntity preview(@Valid PreviewRequest request) {
        var view = BuiltinSchemas.asView(objectMapper, request.view());

        var entities = entityStore.getAllByKind(view.selector().entityKind(), request.limit).stream()
                .map(Entity::asEntityLike);

        var parameters = request.parameters().orElseGet(Map::of);

        return viewProcessor.render(view, parameters, entities);
    }

    private EntityLike assertViewEntity(@Valid RenderRequest request) {
        if (request.viewId().isPresent()) {
            return entityStore.getById(request.viewId().get())
                    .orElseThrow(() -> ApiException.notFound("View not found: " + request.viewId().get()));
        }

        if (request.viewName().isPresent()) {
            return entityStore.getByName(request.viewName().get())
                    .orElseThrow(() -> ApiException.notFound("View not found: " + request.viewName().get()));
        }

        throw ApiException.badRequest("viewId or viewName is required");
    }

    public record RenderRequest(Optional<EntityId> viewId,
            Optional<String> viewName,
            int limit,
            Optional<Map<String, JsonNode>> parameters) {
    }

    public record PreviewRequest(@NotNull PartialEntity view, int limit, Optional<Map<String, JsonNode>> parameters) {
    }
}
