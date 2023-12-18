package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.*;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.server.data.BuiltinSchemas;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.data.ViewRenderer;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jooq.DSLContext;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static ca.ibodrov.mica.server.data.ViewRenderer.interpolate;
import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "View")
@Path("/api/mica/v1/view")
@Produces(APPLICATION_JSON)
public class ViewResource implements Resource {

    private static final String RESULT_ENTITY_KIND = "MicaMaterializedView/v1";

    private final EntityStore entityStore;
    private final ObjectMapper objectMapper;
    private final DSLContext dsl;
    private final ViewRenderer viewRenderer;

    @Inject
    public ViewResource(EntityStore entityStore,
                        ObjectMapper objectMapper,
                        @MicaDB DSLContext dsl) {

        this.entityStore = requireNonNull(entityStore);
        this.objectMapper = requireNonNull(objectMapper);
        this.dsl = requireNonNull(dsl);
        this.viewRenderer = new ViewRenderer(objectMapper);
    }

    @POST
    @Path("render")
    @Consumes(APPLICATION_JSON)
    @Operation(summary = "Render a view", operationId = "render")
    public PartialEntity render(@Valid RenderRequest request) {
        var view = BuiltinSchemas.asViewLike(objectMapper, assertViewEntity(request));
        var parameters = request.parameters().orElseGet(Map::of);
        var entities = fetch(view, parameters, request.limit());
        var renderedView = viewRenderer.render(view, parameters, entities);
        return toEntity(view.name(), objectMapper.convertValue(renderedView.data(), JsonNode.class));
    }

    @GET
    @Path("render/{viewId}")
    @Operation(summary = "Render a simple view (without parameters)", operationId = "renderSimple")
    public PartialEntity renderSimple(@PathParam("viewId") EntityId viewId,
                                      @QueryParam("limit") @DefaultValue("-1") int limit) {

        return render(new RenderRequest(Optional.of(viewId), Optional.empty(), limit, Optional.empty()));
    }

    @POST
    @Path("/preview")
    @Consumes(APPLICATION_JSON)
    @Operation(summary = "Preview a view", operationId = "preview")
    public PartialEntity preview(@Valid PreviewRequest request) {
        var view = BuiltinSchemas.asViewLike(objectMapper, request.view());
        var parameters = request.parameters().orElseGet(Map::of);
        var entities = fetch(view, parameters, request.limit());
        var renderedView = viewRenderer.render(view, parameters, entities);
        return toEntity(view.name(), objectMapper.convertValue(renderedView.data(), JsonNode.class));
    }

    @POST
    @Path("/materialize")
    @Consumes(APPLICATION_JSON)
    @Operation(summary = "Materialize a view", description = "Render a view and save the result as entities", operationId = "materialize")
    public PartialEntity materialize(@Valid RenderRequest request) {
        var view = BuiltinSchemas.asViewLike(objectMapper, assertViewEntity(request));
        var parameters = request.parameters().orElseGet(Map::of);
        var entities = fetch(view, parameters, request.limit());
        var renderedView = viewRenderer.render(view, parameters, entities);
        // TODO optimistic locking
        return dsl.transactionResult(tx -> {
            var data = renderedView.data().stream().map(row -> {
                var entity = objectMapper.convertValue(row, PartialEntity.class);
                var version = entityStore.upsert(tx.dsl(), entity)
                        .orElseThrow(() -> ApiException.conflict("Version conflict: " + entity.name()));
                return entity.withVersion(version);
            });
            return toEntity(view.name(), objectMapper.convertValue(data, JsonNode.class));
        });
    }

    private Stream<Entity> fetch(ViewLike view, Map<String, JsonNode> parameters, int limit) {
        // grab all entities matching the selector's entity kind
        var entities = entityStore.getAllByKind(view.selector().entityKind(), limit);

        // if namePatterns are specified, filter the entities and return them in the
        // order of the patterns
        var result = entities.stream();
        if (view.selector().namePatterns().isPresent()) {
            // interpolate patterns, ignore any unknown variables
            var patterns = view.selector().namePatterns().get().stream()
                    .map(p -> interpolate(p, parameters, false)
                            .replace("$", "\\$")
                            .replace("{", "\\{"))
                    .toList();
            result = Stream.empty();
            for (var pattern : patterns) {
                result = Stream.concat(result, entities.stream().filter(e -> e.name().matches(pattern)));
            }
        }

        return result;
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

    private PartialEntity toEntity(String name, JsonNode data) {
        return PartialEntity.create(name, RESULT_ENTITY_KIND,
                Map.of("data", data,
                        "length", IntNode.valueOf(data.size())));

    }

    public record RenderRequest(Optional<EntityId> viewId,
            Optional<String> viewName,
            int limit, // TODO limit is not very useful right now
            Optional<Map<String, JsonNode>> parameters) {

        public static RenderRequest of(String viewName, int limit) {
            return new RenderRequest(Optional.empty(), Optional.of(viewName), limit, Optional.empty());
        }

        public static RenderRequest parameterized(String viewName, Map<String, JsonNode> parameters, int limit) {
            return new RenderRequest(Optional.empty(), Optional.of(viewName), limit, Optional.of(parameters));
        }
    }

    public record PreviewRequest(@NotNull PartialEntity view, int limit, Optional<Map<String, JsonNode>> parameters) {
    }
}
