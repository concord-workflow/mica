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

import static ca.ibodrov.mica.schema.ObjectSchemaNode.*;
import static ca.ibodrov.mica.server.data.BuiltinSchemas.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuiltinSchemasTest {

    private static ObjectMapper yamlMapper;

    @BeforeAll
    public static void setUp() {
        yamlMapper = new ObjectMapperProvider().get().copyWith(new YAMLFactory());
    }

    @Test
    public void testMigration() {
        var yaml = """
                kind: /mica/kind/v1
                name: foobar
                schema:
                  type: object
                  properties:
                    foo:
                      type: string
                  required:
                    - foo
                """;

        var entity = parseYaml(yaml);
        var input = yamlMapper.convertValue(entity, JsonNode.class);

        var OLD_MICA_KIND_V1_SCHEMA = object(Map.of(
                "id", string(),
                "kind", enums(TextNode.valueOf(MICA_KIND_V1)),
                "name", string(),
                MICA_KIND_SCHEMA_PROPERTY, any()),
                Set.of("kind", "name", "schema"));

        var NEW_MICA_KIND_V1_SCHEMA = BuiltinSchemas.MICA_KIND_V1_SCHEMA;

        var oldResult = Validator.validateObject(OLD_MICA_KIND_V1_SCHEMA, input);
        assertTrue(oldResult.isValid());
        var newResult = Validator.validateObject(NEW_MICA_KIND_V1_SCHEMA, input);
        assertTrue(newResult.isValid());
    }

    @Test
    public void testBadViews() {
        var entity = parseYaml("""
                name: MyView
                kind: /mica/view/v1
                # broken parameters
                parameters:
                  clientId:
                selector:
                  entityKind: /mica/record/v1
                data:
                  jsonPath: $
                """);

        assertThrows(ApiException.class, () -> asView(yamlMapper, entity));
    }

    private static PartialEntity parseYaml(@Language("yaml") String yaml) {
        try {
            return yamlMapper.readValue(yaml, PartialEntity.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
