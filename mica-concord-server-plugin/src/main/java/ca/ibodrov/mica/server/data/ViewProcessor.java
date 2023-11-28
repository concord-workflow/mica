package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.PartialEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class ViewProcessor {

    private static final String RESULT_ENTITY_KIND = "MicaMaterializedView/v1";

    private final ObjectMapper objectMapper;
    private final ParseContext parseContext;

    public ViewProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.parseContext = JsonPath.using(Configuration.builder()
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
                })
                .build());
    }

    /**
     * Materializes a MicaView/v1
     */
    public PartialEntity process(EntityLike view, Stream<EntityLike> entities) {
        if (!view.kind().equals(BuiltinSchemas.MICA_VIEW_V1)) {
            throw new ViewProcessorException("Expected a MicaView/v1 entity, got: " + view.kind());
        }

        var selectorEntityKind = assertSelectorEntityKind(view);
        var jsonPath = assertJsonPath(view);
        var data = entities.filter(entity -> entity.kind().equals(selectorEntityKind))
                .map(entity -> applyJsonPath(entity.data(), jsonPath))
                .toList();

        return PartialEntity.create(view.name(), RESULT_ENTITY_KIND,
                Map.of("data", objectMapper.convertValue(data, JsonNode.class)));
    }

    private String assertSelectorEntityKind(EntityLike view) {
        return Optional.ofNullable(view.data().get("selector"))
                .map(n -> n.get("entityKind"))
                .map(JsonNode::asText)
                .orElseThrow(() -> new ViewProcessorException("View is missing selector.entityKind"));
    }

    private String assertJsonPath(EntityLike view) {
        return Optional.ofNullable(view.data().get("data"))
                .map(n -> n.get("jsonPath"))
                .map(JsonNode::asText)
                .orElseThrow(() -> new ViewProcessorException("View is missing data.jsonPath"));
    }

    private Optional<JsonNode> applyJsonPath(Map<String, JsonNode> data, String jsonPath) {
        var result = parseContext.parse(data).read(jsonPath);
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
