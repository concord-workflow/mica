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
