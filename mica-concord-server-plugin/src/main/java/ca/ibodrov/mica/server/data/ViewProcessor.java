package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.api.model.ViewLike;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;

import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
     * Materialize (or "render") a MicaView/v1 using the given entities.
     */
    public PartialEntity render(ViewLike view, Stream<EntityLike> entities) {
        var data = entities.filter(entity -> entity.kind().equals(view.selector().entityKind()))
                .map(entity -> applyJsonPath(entity.name(), entity.data(), view.data().jsonPath()))
                .flatMap(Optional::stream)
                .toList();

        if (!data.isEmpty() && view.data().flatten() && data.stream().allMatch(JsonNode::isArray)) {
            data = data.stream()
                    .flatMap(node -> StreamSupport
                            .stream(Spliterators.spliteratorUnknownSize(node.elements(), Spliterator.ORDERED), false))
                    .toList();
        }

        return PartialEntity.create(view.name(), RESULT_ENTITY_KIND,
                Map.of("data", objectMapper.convertValue(data, JsonNode.class)));
    }

    private Optional<JsonNode> applyJsonPath(String entityName, Map<String, JsonNode> data, String jsonPath) {
        Object result;
        try {
            result = parseContext.parse(data).read(jsonPath);
        } catch (IllegalArgumentException | JsonPathException e) {
            throw ApiException.badRequest(ApiException.ErrorKind.BAD_DATA,
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
}
