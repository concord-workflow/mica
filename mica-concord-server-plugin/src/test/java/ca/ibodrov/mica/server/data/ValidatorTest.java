package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.server.data.Validator.SchemaFetcher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ValidatorTest {

    @Test
    public void testSimple() {
        var objectMapper = new ObjectMapperProvider().get();
        var validator = Validator.getDefault(objectMapper, new TestSchemaFetcher());
        var schema = parseYaml(objectMapper, """
                $ref: 'mica://internal/bar'
                properties:
                  foo:
                    type: string
                required: [foo]
                """);
        var input = parseYaml(objectMapper, "{}");
        var result = validator.validateObject(schema, input);
        assertEquals(2, result.messages().size());
    }

    private static class TestSchemaFetcher implements SchemaFetcher {

        @Override
        public Optional<InputStream> fetch(String kind) {
            return Optional.of(new ByteArrayInputStream("""
                    {
                        "type": "object",
                        "properties": {
                            "bar": {
                                "type": "string"
                            }
                        },
                        "required": [
                            "bar"
                        ]
                    }
                    """.getBytes()));
        }
    }

    private static ObjectNode parseYaml(ObjectMapper mapper, @Language("yaml") String s) {
        try {
            return mapper.copyWith(new YAMLFactory()).readValue(s, ObjectNode.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
