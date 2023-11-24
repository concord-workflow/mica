package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.AbstractDatabaseTest;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

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
    }

    @Test
    public void testUploadUnknownEntityKind() {
        var yaml = """
                kind: someRandomKind
                name: foobar
                data: |
                  some text
                """;

        var error = assertThrows(ApiException.class, () -> controller.createOrUpdate(parse(yaml)));
        assertEquals(UNKNOWN_ENTITY_KIND, error.getErrorKind());
    }

    @Test
    public void testUploadBuiltInEntityKind() {
        // TODO add other build-in kinds

        var yaml = """
                kind: MicaRecord/v1
                name: %s
                data: |
                  some text
                """.formatted(randomEntityName());

        controller.createOrUpdate(parse(yaml));
    }

    private static PartialEntity parse(String yaml) {
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
