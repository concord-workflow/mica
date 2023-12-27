package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.schema.Validator;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static ca.ibodrov.mica.api.kinds.MicaKindV1.MICA_KIND_V1;
import static ca.ibodrov.mica.api.kinds.MicaKindV1.SCHEMA_PROPERTY;
import static ca.ibodrov.mica.schema.ObjectSchemaNode.*;
import static ca.ibodrov.mica.server.data.BuiltinSchemas.asViewLike;
import static org.junit.jupiter.api.Assertions.*;

public class BuiltinSchemasTest {

    private static ObjectMapper yamlMapper;
    private static BuiltinSchemas builtinSchemas;
    private static Validator validator;

    @BeforeAll
    public static void setUp() {
        yamlMapper = new ObjectMapperProvider().get().copyWith(new YAMLFactory());
        builtinSchemas = new BuiltinSchemas(yamlMapper);
        validator = new Validator(ref -> builtinSchemas.get(ref));
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

        var oldSchema = object(Map.of(
                "id", string(),
                "kind", enums(TextNode.valueOf(MICA_KIND_V1)),
                "name", string(),
                SCHEMA_PROPERTY, any()),
                Set.of("kind", "name", SCHEMA_PROPERTY));

        var newSchema = builtinSchemas.getMicaKindV1Schema();

        var oldResult = validator.validateObject(oldSchema, input);
        assertTrue(oldResult.isValid());
        var newResult = validator.validateObject(newSchema, input);
        assertTrue(newResult.isValid());
    }

    @Test
    public void testBadViews() {
        var entity = parseEntityYaml("""
                name: MyView
                kind: /mica/view/v1
                # broken parameters
                parameters:
                  foo:
                selector:
                  entityKind: /mica/record/v1
                data:
                  jsonPath: $
                """);

        assertThrows(ApiException.class, () -> asViewLike(yamlMapper, entity));
    }

    @Test
    public void testObjectSchemaNodeSchemaValidation() throws Exception {
        var schema = builtinSchemas.getObjectSchemaNodeSchema();

        // invalid type
        var yaml = """
                type: foo
                """;
        var result = validator.validateObject(schema, yamlMapper.readTree(yaml));
        assertFalse(result.isValid());

        // invalid property type
        yaml = """
                type: object
                properties:
                  foo:
                    type: bar
                """;
        result = validator.validateObject(schema, yamlMapper.readTree(yaml));
        assertFalse(result.isValid());
    }

    private static PartialEntity parseEntityYaml(@Language("yaml") String yaml) {
        try {
            return yamlMapper.readValue(yaml, PartialEntity.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
