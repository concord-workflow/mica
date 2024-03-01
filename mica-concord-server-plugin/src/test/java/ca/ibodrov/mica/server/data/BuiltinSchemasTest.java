package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.YamlMapper;
import ca.ibodrov.mica.server.data.Validator.BuiltinSchemasFetcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuiltinSchemasTest {

    private static ObjectMapper objectMapper;
    private static YamlMapper yamlMapper;
    private static BuiltinSchemas builtinSchemas;
    private static Validator validator;

    @BeforeAll
    public static void setUp() {
        objectMapper = new ObjectMapperProvider().get();
        yamlMapper = new YamlMapper(objectMapper);
        builtinSchemas = new BuiltinSchemas(objectMapper);
        validator = Validator.getDefault(objectMapper, new BuiltinSchemasFetcher(builtinSchemas, objectMapper));
    }

    @Test
    public void testMigration() {
        var entity = parseEntityYaml("""
                kind: /mica/kind/v1
                name: foobar
                schema:
                  type: object
                  properties:
                    foo:
                      type: string
                  required:
                    - foo
                """);

        var input = yamlMapper.convertValue(entity, JsonNode.class);

        var oldSchema = parseObject("""
                properties:
                  id:
                    type: string
                  kind:
                    type: string
                  name:
                    type: string
                  schema:
                    type: object
                required: ["kind", "name", "schema"]
                """);

        var newSchema = builtinSchemas.getMicaKindV1Schema();

        var oldResult = validator.validateObject(oldSchema, input);
        assertTrue(oldResult.isValid());
        var newResult = validator.validateObject(newSchema, input);
        assertTrue(newResult.isValid());
    }

    @Test
    public void dropPropertiesAreValidated() {
        var invalidView = parseEntityYaml("""
                kind: /mica/view/v1
                name: /foobar
                selector:
                  entityKind: .*
                data:
                  jsonPath: $.
                  dropProperties: 123 # should be an array
                """);

        var result = validator.validateObject(builtinSchemas.getMicaViewV1Schema(),
                yamlMapper.convertValue(invalidView, JsonNode.class));
        assertFalse(result.isValid());
        assertTrue(result.messages().stream()
                .anyMatch(m -> m.getMessage().contains("dropProperties: integer found, array expected")));

        var validView = parseEntityYaml("""
                kind: /mica/view/v1
                name: /foobar
                selector:
                  entityKind: .*
                data:
                  jsonPath: $.
                  dropProperties:
                    - foo
                    - bar
                """);

        result = validator.validateObject(builtinSchemas.getMicaViewV1Schema(),
                yamlMapper.convertValue(validView, JsonNode.class));
        assertTrue(result.isValid());
    }

    private static PartialEntity parseEntityYaml(@Language("yaml") String yaml) {
        try {
            return yamlMapper.readValue(yaml, PartialEntity.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ObjectNode parseObject(@Language("yaml") String s) {
        try {
            return yamlMapper.readValue(s, ObjectNode.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
