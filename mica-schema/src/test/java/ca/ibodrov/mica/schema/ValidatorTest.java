package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module());

    private final Validator validator = new Validator(objectMapper);

    @Test
    public void testSimpleRender() {
        var schema = p("""
                {
                    "properties": {
                        "username": {
                            "type": "string"
                        }
                    }
                }
                """);

        var props = validator.validateMap(schema, Map.of("username", "bob", "role", "builder"))
                .properties();

        assertValidProperty(props, "username", TextNode.valueOf("bob"));
    }

    @Test
    public void testValidationErrors() {
        var schema = p("""
                {
                   "properties": {
                     "username": {
                       "type": "string"
                     },
                     "password": {
                       "type": "string"
                     },
                     "age": {
                       "type": "number"
                     }
                   },
                   "required": ["username"]
                 }
                """);

        var props = validator.validateMap(schema, Map.of("age", "not a number, clearly"))
                .properties();

        assertInvalidProperty(props, "age");
        assertInvalidProperty(props, "username");
    }

    private static void assertInvalidProperty(Map<String, ValidatedProperty> properties, String key) {
        assertTrue(properties.containsKey(key));
        assertTrue(properties.get(key).value().isEmpty());
        assertTrue(properties.get(key).error().isPresent());
    }

    private static void assertValidProperty(Map<String, ValidatedProperty> properties,
                                            String key,
                                            JsonNode expectedValue) {
        assertTrue(properties.containsKey(key));
        assertTrue(properties.get(key).value().map(v -> v.equals(expectedValue)).orElse(false));
        assertTrue(properties.get(key).error().isEmpty());
    }

    private ObjectSchemaNode p(@Language("JSON") String s) {
        try {
            return objectMapper.readValue(s, ObjectSchemaNode.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
