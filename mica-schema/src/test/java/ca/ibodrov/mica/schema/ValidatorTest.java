package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .setDefaultPropertyInclusion(NON_NULL);

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

    @Test
    public void testConst() {
        var schema = p("""
                {
                   "properties": {
                     "constString": {
                       "const": "foo"
                     },
                     "constNumber": {
                       "const": 123
                     },
                     "constObject": {
                       "const": {
                         "x": 1,
                         "y": 2
                       }
                     },
                     "age": {
                       "type": "number"
                     }
                   },
                   "required": ["constString", "constNumber", "constObject"]
                }
                """);

        var props = validator.validateMap(schema, Map.of("constString", "foo")).properties();
        assertValidProperty(props, "constString", TextNode.valueOf("foo"));
        assertInvalidProperty(props, "constObject");
        assertInvalidProperty(props, "constNumber");

        props = validator.validateMap(schema, Map.of("constString", "bar")).properties();
        assertInvalidProperty(props, "constString");
        assertInvalidProperty(props, "constObject");
        assertInvalidProperty(props, "constNumber");

        props = validator.validateMap(schema, Map.of("constObject", Map.of("x", 1, "y", 2))).properties();
        assertValidProperty(props, "constObject", toJsonNode(Map.of("x", 1, "y", 2)));
        assertInvalidProperty(props, "constString");
        assertInvalidProperty(props, "constNumber");
    }

    @Test
    public void testAny() {
        var schema = p("""
                {
                   "properties": {
                     "anyValue": {
                       "type": "any"
                     }
                   },
                   "required": ["anyValue"]
                }
                """);

        var props = validator.validateMap(schema, Map.of("anyValue", "foo")).properties();
        assertValidProperty(props, "anyValue", TextNode.valueOf("foo"));
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

    private JsonNode toJsonNode(Object o) {
        return objectMapper.convertValue(o, JsonNode.class);
    }
}
