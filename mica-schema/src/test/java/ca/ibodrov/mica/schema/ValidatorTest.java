package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
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

    private ValidatedProperty validateMap(ObjectSchemaNode schema, Map<String, Object> m) {
        var input = objectMapper.convertValue(m, JsonNode.class);
        return Validator.validateObject(schema, input);
    }

    @Test
    public void testSimpleRender() {
        var schema = parseJson(ObjectSchemaNode.class, """
                {
                    "properties": {
                        "username": {
                            "type": "string"
                        }
                    }
                }
                """);

        var result = validateMap(schema, Map.of("username", "bob", "role", "builder"));
        assertValidProperty(result, "username", TextNode.valueOf("bob"));
    }

    @Test
    public void testValidationErrors() {
        var schema = parseJson(ObjectSchemaNode.class, """
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

        var result = validateMap(schema, Map.of("age", "not a number, clearly"));
        assertInvalidProperty(result, "age");
        assertInvalidProperty(result, "username");
    }

    @Test
    public void testEnum() {
        var schema = parseJson(ObjectSchemaNode.class, """
                {
                   "properties": {
                     "constString": {
                       "enum": ["foo", "bar"]
                     },
                     "constNumber": {
                       "enum": [123]
                     },
                     "constObject": {
                       "enum": [{
                         "x": 1,
                         "y": 2
                       }, { "a": true, "b":  false }]
                     },
                     "age": {
                       "type": "number"
                     }
                   },
                   "required": ["constString", "constNumber", "constObject"]
                }
                """);

        var result = validateMap(schema, Map.of("constString", "foo"));
        assertValidProperty(result, "constString", TextNode.valueOf("foo"));
        assertInvalidProperty(result, "constObject");
        assertInvalidProperty(result, "constNumber");

        result = validateMap(schema, Map.of("constString", "baz"));
        assertInvalidProperty(result, "constString");
        assertInvalidProperty(result, "constObject");
        assertInvalidProperty(result, "constNumber");

        result = validateMap(schema, Map.of("constObject", Map.of("x", 1, "y", 2)));
        assertValidProperty(result, "constObject", toJsonNode(Map.of("x", 1, "y", 2)));
        assertInvalidProperty(result, "constString");
        assertInvalidProperty(result, "constNumber");
    }

    @Test
    public void testAny() {
        var schema = parseJson(ObjectSchemaNode.class, """
                {
                   "properties": {
                     "anyValue": {
                       "type": "any"
                     }
                   },
                   "required": ["anyValue"]
                }
                """);

        var result = validateMap(schema, Map.of("anyValue", "foo"));
        assertValidProperty(result, "anyValue", TextNode.valueOf("foo"));
    }

    @Test
    public void testNull() {
        // null types cannot be a required property
        var schema = parseJson(ObjectSchemaNode.class, """
                {
                   "properties": {
                     "nullValue": {
                       "type": "null"
                     }
                   },
                   "required": ["nullValue"]
                }
                """);

        var result = validateMap(schema, Map.of());
        assertInvalidProperty(result, "nullValue");
    }

    @Test
    public void testComplex() {
        var schema = parseYaml(ObjectSchemaNode.class, """
                type: object
                properties:
                  id:
                    type: string
                  kind:
                    enum: [MicaEntityView/v1]
                  name:
                    type: string
                  selector:
                    type: object
                    properties:
                      kind:
                        type: string
                  fields:
                    type: any
                """);

        var input = parseYaml(Map.class, """
                kind: MicaEntityView/v1
                name: whatevs
                data:
                  selector:
                    kind: MicaRecord/v1
                  fields:
                    - name: foo
                      $ref: /properties/foo
                """);

        var result = validateMap(schema, input);
        assertValidProperty(result, "name", TextNode.valueOf("whatevs"));
    }

    private static void assertInvalidProperty(ValidatedProperty property, String key) {
        var properties = property.properties().orElseGet(Map::of);
        assertTrue(properties.containsKey(key));
        assertTrue(properties.get(key).value().isEmpty());
        assertTrue(properties.get(key).error().isPresent());
    }

    private static void assertValidProperty(ValidatedProperty property,
                                            String key,
                                            JsonNode expectedValue) {
        var properties = property.properties().orElseGet(Map::of);
        assertTrue(properties.containsKey(key));
        assertTrue(properties.get(key).value().map(v -> v.equals(expectedValue)).orElse(false));
        assertTrue(properties.get(key).error().isEmpty());
    }

    private <T> T parseJson(Class<T> klass, @Language("JSON") String s) {
        try {
            return objectMapper.readValue(s, klass);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T parseYaml(Class<T> klass, @Language("yaml") String s) {
        try {
            return objectMapper.copyWith(new YAMLFactory())
                    .readValue(s, klass);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode toJsonNode(Object o) {
        return objectMapper.convertValue(o, JsonNode.class);
    }
}
