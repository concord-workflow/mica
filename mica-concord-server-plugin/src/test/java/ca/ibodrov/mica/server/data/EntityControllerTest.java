package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.AbstractDatabaseTest;
import ca.ibodrov.mica.server.YamlMapper;
import ca.ibodrov.mica.server.exceptions.ApiException;
import ca.ibodrov.mica.server.exceptions.EntityValidationException;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EntityControllerTest extends AbstractDatabaseTest {

    private static YamlMapper yamlMapper;
    private static EntityController controller;

    @BeforeAll
    public static void setUp() {
        yamlMapper = new YamlMapper(objectMapper);
        var entityStore = new EntityStore(dsl(), objectMapper, uuidGenerator);
        var builtinSchemas = new BuiltinSchemas(objectMapper);
        var entityKindStore = new EntityKindStore(entityStore, builtinSchemas, objectMapper);
        controller = new EntityController(entityStore, entityKindStore, objectMapper);

        // insert the built-in entity kinds
        new InitialDataLoader(builtinSchemas, entityStore, objectMapper).load();
    }

    @Test
    public void testUploadUnknownEntityKind() {
        var yaml = """
                kind: someRandomKind
                name: foobar
                data: |
                  some text
                """;

        var error = assertThrows(ApiException.class, () -> controller.createOrUpdate(parseYaml(yaml)));
        assertEquals(BAD_REQUEST, error.getStatus());
    }

    @Test
    public void testUploadBuiltInEntityKinds() {
        controller.createOrUpdate(parseYaml("""
                kind: /mica/record/v1
                name: %s
                data: |
                  some text
                """.formatted(randomEntityName())));

        controller.createOrUpdate(parseYaml("""
                kind: /mica/kind/v1
                name: %s
                schema:
                  type: object
                  properties:
                    foo:
                      type: string
                """.formatted(randomEntityName())));

        controller.createOrUpdate(parseYaml("""
                kind: /mica/view/v1
                name: %s
                selector:
                  entityKind: /mica/record/v1
                data:
                  jsonPath: $.data
                """.formatted(randomEntityName())));
    }

    @Test
    public void testUploadInvalidEntity() {
        // missing property
        var entity1 = parseYaml("""
                kind: /mica/record/v1
                name: %s
                randomProp: "foo"
                """.formatted(randomEntityName()));
        var error1 = assertThrows(EntityValidationException.class, () -> controller.createOrUpdate(entity1));
        assertEquals(1, error1.getErrors().size());

        // invalid type
        // TODO test with numbers, booleans, etc.
        var entity2 = parseYaml("""
                kind: /mica/record/v1
                name: null
                data: "foo"
                """);
        var error2 = assertThrows(EntityValidationException.class, () -> controller.createOrUpdate(entity2));
        assertEquals(1, error2.getErrors().size());
    }

    private static PartialEntity parseYaml(@Language("yaml") String yaml) {
        try {
            return yamlMapper.readValue(yaml, PartialEntity.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String randomEntityName() {
        return "test_" + UUID.randomUUID();
    }
}
