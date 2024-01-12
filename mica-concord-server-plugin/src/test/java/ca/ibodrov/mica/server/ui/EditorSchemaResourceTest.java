package ca.ibodrov.mica.server.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static ca.ibodrov.mica.server.ui.EditorSchemaResource.findReplace;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EditorSchemaResourceTest {

    private static final ObjectMapper objectMapper = new ObjectMapperProvider().get();

    @Test
    public void validateFindReplace() {
        var input = parseYaml("""
                allOf: [{$ref: "mica://internal/mica/standard-properties/v1"}]
                properties:
                  kind:
                    $ref: "mica://internal/mica/standard-properties/v1/#/properties/kind"
                deep:
                  deeper: [
                    { $ref: "mica://internal/mica/standard-properties/v1" }
                  ]
                """);

        var output = findReplace(input, "$ref",
                s -> s.replace("mica://internal/mica/standard-properties/v1", "replaced"));
        assertEquals("replaced", output.get("allOf").get(0).get("$ref").asText());
        assertEquals("replaced/#/properties/kind", output.get("properties").get("kind").get("$ref").asText());
        assertEquals("replaced", output.get("deep").get("deeper").get(0).get("$ref").asText());
    }

    private static ObjectNode parseYaml(@Language("yaml") String s) {
        try {
            return objectMapper.copyWith(new YAMLFactory()).readValue(s, ObjectNode.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
