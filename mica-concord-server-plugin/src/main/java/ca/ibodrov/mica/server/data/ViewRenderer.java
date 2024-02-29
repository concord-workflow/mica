package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.ViewLike;
import ca.ibodrov.mica.server.exceptions.ApiException;
import ca.ibodrov.mica.server.exceptions.ViewProcessorException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.zjsonpatch.InvalidJsonPatchException;
import com.flipkart.zjsonpatch.JsonPatch;
import com.flipkart.zjsonpatch.JsonPatchApplicationException;
import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;

public class ViewRenderer {

    private final ObjectMapper objectMapper;
    private final ParseContext parseContext;

    public ViewRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        this.parseContext = JsonPath.using(Configuration.builder()
                .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
                // a custom JsonProvider that supports both JsonNode and Map
                .jsonProvider(new MicaJsonProvider(objectMapper))
                .build());
    }

    public RenderedView render(ViewLike view, Stream<? extends EntityLike> entities) {
        return render(view, RenderOverrides.none(), entities);
    }

    /**
     * Render a /mica/view/v1 using the given entities and parameters.
     */
    public RenderedView render(ViewLike view, RenderOverrides overrides, Stream<? extends EntityLike> entities) {
        // interpolate JSON path using the supplied parameters
        var jsonPath = requireNonNull(view.data().jsonPath());

        var entityNames = ImmutableList.<String>builder();

        // apply JSON path
        var data = entities
                .peek(entity -> entityNames.add(entity.name()))
                .map(row -> applyJsonPath(row.name(), objectMapper.convertValue(row, JsonNode.class), jsonPath))
                .flatMap(Optional::stream)
                .toList();

        if (data.isEmpty()) {
            return RenderedView.empty(view, entityNames.build());
        }

        // flatten - convert an array of arrays into a single array by concatenating
        // them
        var flatten = view.data().flatten().orElse(false);
        if (flatten && data.stream().allMatch(JsonNode::isArray)) {
            data = data.stream()
                    .flatMap(node -> stream(spliteratorUnknownSize(node.elements(), Spliterator.ORDERED), false))
                    .toList();
        }

        // merge - convert an array of objects into a single object
        var merge = overrides.alwaysMerge() || view.data().merge().orElse(false);
        if (merge && data.stream().allMatch(JsonNode::isObject)) {
            var mergedData = data.stream()
                    .reduce((a, b) -> deepMerge((ObjectNode) a, (ObjectNode) b))
                    .orElseThrow(() -> new ViewProcessorException("Expected a merge result, got nothing"));
            data = List.of(mergedData);
        }

        // apply JSON patch
        var patch = view.data().jsonPatch().filter(p -> !p.isNull());
        if (patch.isPresent()) {
            var patchData = patch.get();
            try {
                JsonPatch.validate(patchData);
            } catch (InvalidJsonPatchException e) {
                throw new ViewProcessorException("Invalid data.jsonPatch: " + e.getMessage());
            }

            data = data.stream()
                    .map(node -> applyJsonPatch(node, patchData))
                    .toList();
        }

        // drop properties if requested
        var dropProperties = view.data().dropProperties().orElse(List.of());
        if (!dropProperties.isEmpty()) {
            data.forEach(node -> {
                if (!node.isObject()) {
                    throw new ViewProcessorException(
                            "dropProperties can only be applied to arrays of objects. The data is an array of %ss"
                                    .formatted(node.getNodeType()));
                }
                ((ObjectNode) node).remove(dropProperties);
            });
        }

        return new RenderedView(view, data, entityNames.build());
    }

    private Optional<JsonNode> applyJsonPath(String entityName, JsonNode data, String jsonPath) {
        Object result;
        try {
            result = parseContext.parse(data).read(jsonPath);
        } catch (IllegalArgumentException | JsonPathException e) {
            throw ApiException.badRequest(
                    "Error while processing entity '%s'. %s (%s)".formatted(entityName, e.getMessage(), jsonPath));
        }
        if (result == null || result instanceof NullNode) {
            return Optional.empty();
        }
        if (!(result instanceof JsonNode)) {
            try {
                result = objectMapper.convertValue(result, JsonNode.class);
            } catch (IllegalArgumentException e) {
                throw new ViewProcessorException("Expected a JsonNode, got: " + result.getClass());
            }
        }
        return Optional.of((JsonNode) result);
    }

    private JsonNode applyJsonPatch(JsonNode node, JsonNode patchData) {
        if (!node.isContainerNode()) {
            throw new ViewProcessorException(
                    "JSON patch can only be applied to arrays of objects and array of arrays. The data is an array of %ss"
                            .formatted(node.getNodeType()));
        }

        try {
            return JsonPatch.apply(patchData, node);
        } catch (JsonPatchApplicationException e) {
            throw new ViewProcessorException(
                    "Error while applying data.jsonPatch: " + e.getMessage());
        }
    }

    private ObjectNode deepMerge(ObjectNode left, ObjectNode right) {
        var mapper = objectMapper.copy();

        mapper.configOverride(ArrayNode.class)
                .setMergeable(false);

        try {
            return mapper.updateValue(left, right);
        } catch (JsonProcessingException e) {
            throw new ViewProcessorException("Error while merging JSON objects: " + e.getMessage());
        }
    }

    public record RenderOverrides(boolean alwaysMerge) {

        public static RenderOverrides none() {
            return new RenderOverrides(false);
        }

        public static RenderOverrides merged() {
            return new RenderOverrides(true);
        }
    }

    private static class MicaJsonProvider extends JacksonJsonNodeJsonProvider {

        public MicaJsonProvider(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        public boolean isMap(Object obj) {
            return super.isMap(obj) || obj instanceof Map;
        }

        @Override
        public Object getMapValue(Object obj, String key) {
            if (obj instanceof ObjectNode) {
                return super.getMapValue(obj, key);
            }
            if (obj instanceof Map) {
                return ((Map<?, ?>) obj).get(key);
            }
            throw new ViewProcessorException("Expected a Map, got: " + obj.getClass());
        }

        @Override
        public String toString() {
            return "mica-json-provider";
        }
    }
}
