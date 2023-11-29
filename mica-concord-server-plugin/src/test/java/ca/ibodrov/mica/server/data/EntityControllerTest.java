package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.AbstractDatabaseTest;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static ca.ibodrov.mica.server.exceptions.ApiException.ErrorKind.BAD_DATA;
import static ca.ibodrov.mica.server.exceptions.ApiException.ErrorKind.UNKNOWN_ENTITY_KIND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EntityControllerTest extends AbstractDatabaseTest {

    private static ObjectMapper yamlMapper;
    private static EntityController controller;

    @BeforeAll
    public static void setUp() {
        yamlMapper = objectMapper.copyWith(new YAMLFactory());
        var entityStore = new EntityStore(dsl(), objectMapper, uuidGenerator);
        var entityKindStore = new EntityKindStore(entityStore, objectMapper);
        controller = new EntityController(entityStore, entityKindStore, objectMapper);

        // insert the built-in entity kinds
        new InitialDataLoader(entityStore, objectMapper).load();
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
        assertEquals(UNKNOWN_ENTITY_KIND, error.getErrorKind());
    }

    @Test
    public void testUploadBuiltInEntityKinds() {
        controller.createOrUpdate(parseYaml("""
                kind: MicaRecord/v1
                name: %s
                data: |
                  some text
                """.formatted(randomEntityName())));

        controller.createOrUpdate(parseYaml("""
                kind: MicaKind/v1
                name: %s
                schema:
                  type: object
                  properties:
                    foo:
                      type: string
                """.formatted(randomEntityName())));

        controller.createOrUpdate(parseYaml("""
                kind: MicaView/v1
                name: %s
                selector:
                  kind: MicaRecord/v1
                data:
                  jsonPath: $.data
                """.formatted(randomEntityName())));
    }

    @Test
    public void testUploadInvalidEntity() {
        // missing property
        var entity1 = parseYaml("""
                kind: MicaRecord/v1
                name: %s
                randomProp: "foo"
                """.formatted(randomEntityName()));
        var error1 = assertThrows(ApiException.class, () -> controller.createOrUpdate(entity1));
        assertEquals(BAD_DATA, error1.getErrorKind());

        // invalid type
        // TODO test with numbers, booleans, etc.
        var entity2 = parseYaml("""
                kind: MicaRecord/v1
                name: null
                data: "foo"
                """);
        var error2 = assertThrows(ApiException.class, () -> controller.createOrUpdate(entity2));
        assertEquals(BAD_DATA, error2.getErrorKind());
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
