package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.*;
import ca.ibodrov.mica.db.MicaDB;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import static ca.ibodrov.mica.server.data.BuiltinSchemas.INTERNAL_ENTITY_STORE_URI;
import static java.util.Objects.requireNonNull;

public class ViewController {

    private static final Logger log = LoggerFactory.getLogger(ViewController.class);

    private static final String RESULT_ENTITY_KIND = "/mica/rendered-view/v1";

    private final EntityStore entityStore;
    private final EntityKindStore entityKindStore;
    private final EntityFetchers entityFetchers;
    private final ViewInterpolator viewInterpolator;
    private final ViewRenderer viewRenderer;
    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final DSLContext dsl;

    @Inject
    public ViewController(@MicaDB DSLContext dsl,
                          EntityStore entityStore,
                          EntityKindStore entityKindStore,
                          EntityFetchers entityFetchers,
                          JsonPathEvaluator jsonPathEvaluator,
                          ObjectMapper objectMapper) {

        this.entityStore = requireNonNull(entityStore);
        this.entityKindStore = requireNonNull(entityKindStore);
        this.entityFetchers = requireNonNull(entityFetchers);
        this.objectMapper = requireNonNull(objectMapper);
        var schemaFetcher = new EntityKindStoreSchemaFetcher(entityKindStore, objectMapper);
        this.viewInterpolator = new ViewInterpolator(objectMapper, schemaFetcher);
        requireNonNull(jsonPathEvaluator);
        this.viewRenderer = new ViewRenderer(jsonPathEvaluator, objectMapper);
        this.validator = Validator.getDefault(objectMapper, schemaFetcher);
        this.dsl = requireNonNull(dsl);
    }

    public RenderedView render(RenderRequest request) {
        var parameters = request.parameters().orElseGet(NullNode::getInstance);
        var view = interpolateView(assertViewEntity(request), parameters);
        return render(view);
    }

    public PartialEntity renderAsEntity(RenderRequest request) {
        var parameters = request.parameters().orElseGet(NullNode::getInstance);
        var view = interpolateView(assertViewEntity(request), parameters);
        return renderAsEntity(view);
    }

    public String renderProperties(RenderRequest request) {
        var parameters = request.parameters().orElseGet(NullNode::getInstance);
        var view = interpolateView(assertViewEntity(request), parameters);
        var entities = select(view);

        var renderedView = viewRenderer.render(view, ViewRenderer.RenderOverrides.merged(), entities);
        if (renderedView.data().size() != 1) {
            throw ApiException.badRequest("Expected a view flattened down to a single entity, got "
                    + renderedView.data().size() + " entities");
        }

        var validation = validateRenderedView(view, renderedView);
        if (validation.isPresent() && !validation.get().isEmpty()) {
            throw ApiException.badRequest("Validation failed: " + validation.get());
        }

        var properties = formatAsProperties((ObjectNode) renderedView.data().get(0));
        return properties.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .sorted()
                .reduce((a, b) -> a + "\n" + b)
                .orElse("# empty") + "\n";
    }

    public PartialEntity preview(PreviewRequest request) {
        var parameters = request.parameters().orElseGet(NullNode::getInstance);
        var viewEntity = validate(request.view());
        var view = interpolateView(viewEntity, parameters);
        return renderAsEntity(view);
    }

    public PartialEntity materialize(UserPrincipal session, RenderRequest request) {
        var parameters = request.parameters().orElseGet(NullNode::getInstance);
        var view = interpolateView(assertViewEntity(request), parameters);
        var entities = select(view);
        var renderedView = viewRenderer.render(view, entities);
        // TODO validation
        // TODO optimistic locking
        return dsl.transactionResult(tx -> {
            var data = renderedView.data().stream().map(row -> {
                var entity = objectMapper.convertValue(row, PartialEntity.class);
                var version = entityStore.upsert(tx.dsl(), session, entity)
                        .orElseThrow(() -> ApiException.conflict("Version conflict: " + entity.name()));
                return entity.withVersion(version);
            });
            return buildEntity(view.name(),
                    objectMapper.convertValue(data, JsonNode.class),
                    objectMapper.convertValue(renderedView.entityNames(), JsonNode.class),
                    Optional.empty());
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

    private ViewLike interpolateView(EntityLike viewEntity, JsonNode parameters) {
        var view = BuiltinSchemas.asViewLike(objectMapper, viewEntity);
        return viewInterpolator.interpolate(view, parameters);
    }

    private RenderedView render(ViewLike view) {
        var entities = select(view);
        return viewRenderer.render(view, entities);
    }

    private PartialEntity renderAsEntity(ViewLike view) {
        var renderedView = render(view);
        var validation = validateRenderedView(view, renderedView);
        return buildEntity(view.name(),
                objectMapper.convertValue(renderedView.data(), JsonNode.class),
                objectMapper.convertValue(renderedView.entityNames(), JsonNode.class),
                validation);
    }

    /**
     * Applies the view's {@code selector} by fetching entities from the specified
     * includes, filtering them by the entity kind and name patterns, and returning
     * the result.
     */
    private Stream<? extends EntityLike> select(ViewLike view) {
        var includes = view.selector().includes().orElse(List.of(INTERNAL_ENTITY_STORE_URI));

        // grab all entities matching the selector's entity kind
        var entities = includes.stream()
                .filter(include -> include != null && !include.isBlank())
                .map(ViewController::parseUri)
                .flatMap(uri -> entityFetchers.fetch(uri, view.selector().entityKind()))
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
            for (var regex : patterns) {
                Pattern pattern;
                try {
                    pattern = Pattern.compile(regex);
                } catch (PatternSyntaxException e) {
                    throw ApiException.badRequest("Invalid namePatterns pattern: " + regex
                            + view.parameters().map(p -> " (invalid parameters?)").orElse(""));
                }

                result = Stream.concat(result, entities.stream()
                        .filter(e -> e.name() != null)
                        .filter(e -> pattern.matcher(e.name()).matches()));
            }
        }

        return result;
    }

    private Optional<JsonNode> validateRenderedView(ViewLike view, RenderedView renderedView) {
        return view.validation().map(v -> {
            var schema = entityKindStore.getSchemaForKind(v.asEntityKind())
                    .orElseThrow(() -> ApiException
                            .badRequest("Can't validate the view, schema not found: " + v.asEntityKind()));
            var validatedEntities = renderedView.data().stream()
                    .map(row -> validator.validateObject(schema, row))
                    .toList();
            return objectMapper.convertValue(validatedEntities, JsonNode.class);
        });
    }

    private PartialEntity validate(PartialEntity entity) {
        var schema = entityKindStore.getSchemaForKind(entity.kind())
                .orElseThrow(() -> ApiException
                        .badRequest("Can't validate the entity, schema not found: " + entity.kind()));
        var input = objectMapper.convertValue(entity, JsonNode.class);
        var validatedInput = validator.validateObject(schema, input);
        if (!validatedInput.isValid()) {
            throw validatedInput.toException();
        }
        return entity;
    }

    private static PartialEntity buildEntity(String name,
                                             JsonNode data,
                                             JsonNode entityNames,
                                             Optional<JsonNode> validation) {
        var entityData = ImmutableMap.<String, JsonNode>builder();
        entityData.put("data", data);
        entityData.put("length", IntNode.valueOf(data.size()));
        entityData.put("entityNames", entityNames);
        validation.ifPresent(v -> entityData.put("validation", v));
        return PartialEntity.create(name, RESULT_ENTITY_KIND, entityData.build());
    }

    private static URI parseUri(String s) {
        try {
            return URI.create(s);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid URI: " + s);
        }
    }

    @VisibleForTesting
    static Map<String, String> formatAsProperties(ObjectNode node) {
        var builder = ImmutableMap.<String, String>builder();
        putProperty(builder, node, "");
        return builder.build();
    }

    private static void putProperty(ImmutableMap.Builder<String, String> builder, ObjectNode node, String path) {
        node.fields().forEachRemaining(e -> {
            var k = e.getKey();
            var v = e.getValue();
            switch (v.getNodeType()) {
                case OBJECT -> putProperty(builder, (ObjectNode) v, k + ".");
                case NULL -> {
                    // skip
                }
                default -> builder.put(path + k, v.asText());
            }
        });
    }
}
