package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.*;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.schema.Validator;
import ca.ibodrov.mica.server.data.*;
import ca.ibodrov.mica.server.exceptions.ApiException;
import ca.ibodrov.mica.server.exceptions.StoreException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "View")
@Path("/api/mica/v1/view")
@Produces(APPLICATION_JSON)
public class ViewResource implements Resource {

    private static final String RESULT_ENTITY_KIND = "/mica/materializedView/v1";
    public static final String INTERNAL_ENTITY_STORE_URI = URI.create("mica://internal").toString();

    /**
     * Don't validate these properties, they are added by the system.
     */
    private static final Set<String> STANDARD_PROPERTIES = Set.of("id", "kind", "name", "createdAt", "updatedAt");

    private final DSLContext dsl;
    private final EntityStore entityStore;
    private final EntityKindStore entityKindStore;
    private final Set<EntityFetcher> includeFetchers;
    private final ObjectMapper objectMapper;
    private final ViewInterpolator viewInterpolator;
    private final ViewRenderer viewRenderer;
    private final Validator validator;

    @Inject
    public ViewResource(@MicaDB DSLContext dsl,
                        EntityStore entityStore,
                        EntityKindStore entityKindStore,
                        Set<EntityFetcher> includeFetchers,
                        ObjectMapper objectMapper) {

        this.dsl = requireNonNull(dsl);
        this.entityStore = requireNonNull(entityStore);
        this.entityKindStore = requireNonNull(entityKindStore);
        this.includeFetchers = requireNonNull(includeFetchers);
        this.objectMapper = requireNonNull(objectMapper);
        this.viewInterpolator = new ViewInterpolator(entityKindStore::getSchemaForKind);
        this.viewRenderer = new ViewRenderer(objectMapper);
        this.validator = new Validator(entityKindStore::getSchemaForKind);
    }

    @POST
    @Path("render")
    @Consumes(APPLICATION_JSON)
    @Operation(summary = "Render a view", operationId = "render")
    @Validate
    public PartialEntity render(@Valid RenderRequest request) {
        var parameters = request.parameters().orElseGet(NullNode::getInstance);
        var view = interpolateView(assertViewEntity(request), parameters);
        var entities = fetch(view, request.limit());
        var renderedView = viewRenderer.render(view, entities);
        var validation = validate(view, renderedView);
        return toEntity(view.name(), objectMapper.convertValue(renderedView.data(), JsonNode.class), validation);
    }

    @GET
    @Path("render/{viewId}")
    @Operation(summary = "Render a simple view (without parameters)", operationId = "renderSimple")
    @Validate
    public PartialEntity renderSimple(@PathParam("viewId") EntityId viewId,
                                      @QueryParam("limit") @DefaultValue("-1") int limit) {

        return render(new RenderRequest(Optional.of(viewId), Optional.empty(), limit, Optional.empty()));
    }

    @POST
    @Path("/preview")
    @Consumes(APPLICATION_JSON)
    @Operation(summary = "Preview a view", operationId = "preview")
    @Validate
    public PartialEntity preview(@Valid PreviewRequest request) {
        var parameters = request.parameters().orElseGet(NullNode::getInstance);
        var view = interpolateView(request.view(), parameters);
        var entities = fetch(view, request.limit());
        var renderedView = viewRenderer.render(view, entities);
        var validation = validate(view, renderedView);
        return toEntity(view.name(), objectMapper.convertValue(renderedView.data(), JsonNode.class), validation);
    }

    @POST
    @Path("/materialize")
    @Consumes(APPLICATION_JSON)
    @Operation(summary = "Materialize a view", description = "Render a view and save the result as entities", operationId = "materialize")
    @Validate
    public PartialEntity materialize(@Valid RenderRequest request) {
        var parameters = request.parameters().orElseGet(NullNode::getInstance);
        var view = interpolateView(assertViewEntity(request), parameters);
        var entities = fetch(view, request.limit());
        var renderedView = viewRenderer.render(view, entities);
        // TODO validation
        // TODO optimistic locking
        return dsl.transactionResult(tx -> {
            var data = renderedView.data().stream().map(row -> {
                var entity = objectMapper.convertValue(row, PartialEntity.class);
                var version = entityStore.upsert(tx.dsl(), entity)
                        .orElseThrow(() -> ApiException.conflict("Version conflict: " + entity.name()));
                return entity.withVersion(version);
            });
            return toEntity(view.name(), objectMapper.convertValue(data, JsonNode.class), Optional.empty());
        });
    }

    private ViewLike interpolateView(EntityLike viewEntity, JsonNode parameters) {
        var view = BuiltinSchemas.asViewLike(objectMapper, viewEntity);
        return viewInterpolator.interpolate(view, parameters);
    }

    private Stream<? extends EntityLike> fetch(ViewLike view, int limit) {
        // TODO limit is not very useful right now

        var includes = view.selector().includes().orElse(List.of(INTERNAL_ENTITY_STORE_URI));

        // grab all entities matching the selector's entity kind
        var entities = includes.stream()
                .filter(include -> include != null && !include.isBlank())
                .map(ViewResource::parseUri)
                .flatMap(include -> fetchIncludeUri(include, view.selector().entityKind(), limit))
                .toList();

        // TODO filter out invalid entities?

        if (entities.isEmpty()) {
            return Stream.empty();
        }

        var result = entities.stream();
        // if namePatterns are specified, filter the entities and return them in the
        // order of the patterns
        if (view.selector().namePatterns().isPresent()) {
            var patterns = view.selector().namePatterns().get();

            result = Stream.empty();
            for (var pattern : patterns) {
                result = Stream.concat(result, entities.stream()
                        .filter(e -> e.name() != null) // TODO validate the whole entity instead?
                        .filter(e -> e.name().matches(pattern)));
            }
        }

        return result;
    }

    private Stream<EntityLike> fetchIncludeUri(URI include, String entityKind, int limit) {
        // TODO parallel?
        return includeFetchers.stream().flatMap(fetcher -> {
            try {
                return fetcher.getAllByKind(include, entityKind, limit).stream();
            } catch (StoreException e) {
                throw ApiException.internalError(e.getMessage());
            }
        });
    }

    private Optional<JsonNode> validate(ViewLike view, RenderedView renderedView) {
        return view.validation().map(v -> {
            var schema = entityKindStore.getSchemaForKind(v.asEntityKind())
                    .orElseThrow(() -> ApiException
                            .badRequest("Can't validate the view, schema not found: " + v.asEntityKind()));
            var validatedEntities = renderedView.data().stream()
                    .map(row -> validator.validateObject(schema, row, STANDARD_PROPERTIES))
                    .toList();
            return objectMapper.convertValue(validatedEntities, JsonNode.class);
        });
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

    private PartialEntity toEntity(String name, JsonNode data, Optional<JsonNode> validation) {
        return PartialEntity.create(name, RESULT_ENTITY_KIND,
                Map.of("data", data,
                        "length", IntNode.valueOf(data.size()),
                        "validation", validation.orElseGet(NullNode::getInstance)));

    }

    private static URI parseUri(String s) {
        try {
            return URI.create(s);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid URI: " + s);
        }
    }
}
