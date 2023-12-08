package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.api.model.ViewLike;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.zjsonpatch.InvalidJsonPatchException;
import com.flipkart.zjsonpatch.JsonPatch;
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

public class ViewProcessor {

    private static final String RESULT_ENTITY_KIND = "MicaMaterializedView/v1";

    private final ObjectMapper objectMapper;
    private final ParseContext parseContext;

    public ViewProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        this.parseContext = JsonPath.using(Configuration.builder()
                .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
                // a custom JsonProvider that supports both JsonNode and Map
                .jsonProvider(new JacksonJsonNodeJsonProvider(objectMapper) {
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
                })
                .build());
    }

    /**
     * @see #render(ViewLike, Map, Stream)
     */
    public PartialEntity render(ViewLike view, Stream<EntityLike> entities) {
        return render(view, Map.of(), entities);
    }

    /**
     * Materialize (or "render") a /mica/view/v1 using the given entities and
     * parameters.
     */
    public PartialEntity render(ViewLike view, Map<String, JsonNode> parameters, Stream<EntityLike> entities) {
        // TODO validate supplied parameters according to the view's schema

        var jsonPath = requireNonNull(view.data().jsonPath());
        var effectiveJsonPath = interpolate(jsonPath, parameters);

        var data = entities.filter(entity -> entity.kind().equals(view.selector().entityKind()))
                .map(entity -> applyJsonPath(entity.name(), entity.data(), effectiveJsonPath))
                .flatMap(Optional::stream)
                .toList();

        if (!data.isEmpty()) {
            var flatten = view.data().flatten().orElse(false);
            if (flatten && data.stream().allMatch(JsonNode::isArray)) {
                data = data.stream()
                        .flatMap(node -> stream(spliteratorUnknownSize(node.elements(), Spliterator.ORDERED), false))
                        .toList();
            }

            var merge = view.data().merge().orElse(false);
            if (merge && data.stream().allMatch(JsonNode::isObject)) {
                var mergedData = data.stream()
                        .reduce((a, b) -> deepMerge((ObjectNode) a, (ObjectNode) b))
                        .orElseThrow(() -> new ViewProcessorException("Expected a merge result, got nothing"));
                data = List.of(mergedData);
            }

            var patch = view.data().jsonPatch();
            if (patch.isPresent()) {
                var patchData = patch.get();
                if (!patchData.isArray()) {
                    throw new ViewProcessorException(
                            "Expected an array of JSON patch operations in data.jsonPatch, got: "
                                    + patchData.getNodeType());
                }

                try {
                    JsonPatch.validate(patchData);
                } catch (InvalidJsonPatchException e) {
                    throw new ViewProcessorException("Invalid data.jsonPatch: " + e.getMessage());
                }

                data = data.stream()
                        .map(node -> JsonPatch.apply(patchData, node))
                        .toList();
            }
        }

        return PartialEntity.create(view.name(), RESULT_ENTITY_KIND,
                Map.of("data", objectMapper.convertValue(data, JsonNode.class),
                        "length", IntNode.valueOf(data.size())));
    }

    private Optional<JsonNode> applyJsonPath(String entityName, Map<String, JsonNode> data, String jsonPath) {
        Object result;
        try {
            result = parseContext.parse(data).read(jsonPath);
        } catch (IllegalArgumentException | JsonPathException e) {
            throw ApiException.badRequest(
                    "Error while processing entity '%s'. %s (%s)".formatted(entityName, e.getMessage(), jsonPath));
        }
        if (result == null) {
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

    private ObjectNode deepMerge(ObjectNode left, ObjectNode right) {
        right.fieldNames().forEachRemaining(rightKey -> {
            var leftValue = left.get(rightKey);
            var rightValue = right.get(rightKey);

            var result = rightValue;
            if (leftValue instanceof ObjectNode leftObject && rightValue instanceof ObjectNode rightObject) {
                result = deepMerge(leftObject, rightObject);
            }

            left.set(rightKey, result);
        });
        return left;
    }

    private static String interpolate(String s, Map<String, JsonNode> parameters) {
        if (!parameters.isEmpty()) {
            for (var e : parameters.entrySet()) {
                var key = "$" + e.getKey();
                if (!s.contains(key)) {
                    continue;
                }

                var value = e.getValue();
                if (value.isTextual()) {
                    s = s.replace(key, "'" + value.asText() + "'");
                } else {
                    s = s.replace(key, value.asText("unknown"));
                }
            }
        }

        // TODO might not be enough
        if (s.matches(".*\\$[a-zA-Z_]+.*")) {
            throw new ViewProcessorException("Unresolved parameters in JSON path: " + s);
        }

        return s;
    }
}
