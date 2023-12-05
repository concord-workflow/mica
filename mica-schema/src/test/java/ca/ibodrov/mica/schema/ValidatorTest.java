package ca.ibodrov.mica.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static org.junit.jupiter.api.Assertions.*;

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
        assertTrue(result.isValid());
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
        assertTrue(result.isValid());
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
    public void testBoolean() {
        var schema = parseJson(ObjectSchemaNode.class, """
                {
                   "properties": {
                     "maybeTrueValue": {
                       "type": "boolean"
                     },
                     "maybeFalseValue": {
                       "type": "boolean"
                     },
                     "alwaysTrueValue": {
                       "type": "boolean",
                       "enum": [true]
                     },
                     "alwaysFalseValue": {
                       "type": "boolean",
                       "enum": [false]
                     }
                   }
                }
                """);

        var result = validateMap(schema, Map.of(
                "maybeTrueValue", true,
                "maybeFalseValue", false,
                "alwaysTrueValue", true,
                "alwaysFalseValue", false));
        assertTrue(result.isValid());

        assertValidProperty(result, "maybeTrueValue", BooleanNode.getTrue());
        assertValidProperty(result, "maybeFalseValue", BooleanNode.getFalse());
        assertValidProperty(result, "alwaysTrueValue", BooleanNode.getTrue());
        assertValidProperty(result, "alwaysFalseValue", BooleanNode.getFalse());

        result = validateMap(schema, Map.of(
                "maybeTrueValue", false,
                "maybeFalseValue", true,
                "alwaysTrueValue", false,
                "alwaysFalseValue", true));
        assertFalse(result.isValid());

        assertValidProperty(result, "maybeTrueValue", BooleanNode.getFalse());
        assertValidProperty(result, "maybeFalseValue", BooleanNode.getTrue());
        assertInvalidProperty(result, "alwaysTrueValue");
        assertInvalidProperty(result, "alwaysFalseValue");
    }

    @Test
    public void testArray() {
        var schema = parseJson(ObjectSchemaNode.class, """
                {
                    "properties": {
                        "arrayOfArbitraryStrings": {
                            "type": "array",
                            "items": {
                                "type": "string"
                            }
                        },
                        "arrayOfEnumStrings": {
                            "type": "array",
                            "items": {
                                "type": "string",
                                "enum": [
                                    "foo",
                                    "bar"
                                ]
                            }
                        },
                        "arrayOfObjects": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "x": {
                                        "type": "number"
                                    },
                                    "y": {
                                        "type": "number"
                                    }
                                }
                            }
                        }
                    }
                }
                """);

        // all valid
        var result = validateMap(schema, Map.of(
                "arrayOfArbitraryStrings", List.of("abc", "def", "ghi"),
                "arrayOfEnumStrings", List.of("foo", "bar"),
                "arrayOfObjects", List.of(Map.of("x", 1, "y", 2), Map.of("x", 3, "y", 4))));
        assertTrue(result.isValid());

        assertValidProperty(result, "arrayOfArbitraryStrings", toJsonNode(List.of("abc", "def", "ghi")));
        assertValidProperty(result, "arrayOfEnumStrings", toJsonNode(List.of("foo", "bar")));
        assertValidProperty(result, "arrayOfObjects",
                toJsonNode(List.of(Map.of("x", 1, "y", 2), Map.of("x", 3, "y", 4))));

        // array of enum values has an invalid value
        // array of object has an object with an invalid value
        result = validateMap(schema, Map.of(
                "arrayOfArbitraryStrings", List.of("abc", "def", "ghi"),
                "arrayOfEnumStrings", List.of("baz"),
                "arrayOfObjects", List.of(Map.of("x", false, "y", true))));
        assertFalse(result.isValid());

        assertValidProperty(result, "arrayOfArbitraryStrings", toJsonNode(List.of("abc", "def", "ghi")));
        assertInvalidProperty(result, "arrayOfEnumStrings");
        assertInvalidProperty(result, "arrayOfObjects");

        schema = parseJson(ObjectSchemaNode.class, """
                {
                    "properties": {
                        "enumArray": {
                            "type": "array",
                            "items": {
                                "type": "string"
                            },
                            "enum": [
                                [
                                    "foo",
                                    "bar"
                                ],
                                [
                                    "baz",
                                    "qux"
                                ]
                            ]
                        }
                    }
                }
                """);

        // enum array value
        result = validateMap(schema, Map.of(
                "enumArray", List.of("foo", "bar")));
        assertTrue(result.isValid());

        result = validateMap(schema, Map.of(
                "enumArray", List.of("baz", "qux")));
        assertTrue(result.isValid());

        result = validateMap(schema, Map.of(
                "enumArray", List.of("foo", "qux")));
        assertFalse(result.isValid());

        result = validateMap(schema, Map.of(
                "enumArray", List.of("bar", "foo")));
        assertFalse(result.isValid());
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
                  allowThingamajigs:
                    type: boolean
                  fields:
                    type: array
                    items:
                      properties:
                        name:
                          type: string
                        $ref:
                          type: string
                """);

        var input = parseYaml(Map.class, """
                kind: MicaEntityView/v1
                name: whatevs
                selector:
                  kind: /mica/record/v1
                allowThingamajigs: true
                fields:
                  - name: foo
                    $ref: /properties/foo
                  - name: bar
                    $ref: /properties/bar
                """);

        var result = validateMap(schema, input);
        assertTrue(result.isValid());

        assertValidProperty(result, "name", TextNode.valueOf("whatevs"));

        var properties = result.properties().orElseGet(Map::of);
        assertEquals(BooleanNode.getTrue(), properties.get("allowThingamajigs").value().orElseThrow());
        assertEquals("/mica/record/v1", properties.get("selector").properties().orElseThrow()
                .get("kind").value().orElseThrow().asText());
        assertEquals("/properties/foo", properties.get("fields").value().orElseThrow().get(0).get("$ref").asText());
    }

    private static void assertInvalidProperty(ValidatedProperty property, String key) {
        var properties = property.properties().orElseGet(Map::of);
        assertTrue(properties.containsKey(key));
        assertTrue(properties.get(key).error().isPresent() || !properties.get(key).isValid());
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
