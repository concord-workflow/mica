package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.AbstractDatabaseTest;
import ca.ibodrov.mica.server.TestClock;
import ca.ibodrov.mica.server.YamlMapper;
import ca.ibodrov.mica.server.exceptions.ApiException;
import ca.ibodrov.mica.server.exceptions.EntityValidationException;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EntityControllerTest extends AbstractDatabaseTest {

    private static TestClock clock;
    private static YamlMapper yamlMapper;
    private static EntityController controller;
    private static EntityStore entityStore;

    @BeforeAll
    public static void setUp() {
        var instant = Instant.parse("2021-02-03T01:02:03.123456Z");
        clock = TestClock.fixed(instant, ZoneId.of("UTC"));

        yamlMapper = new YamlMapper(objectMapper);
        entityStore = new EntityStore(dsl(), objectMapper, uuidGenerator, clock);
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

    @Test
    public void testDocBeingUpdatedAfterUpsert() {
        var doc = """
                kind: /mica/record/v1
                name: %s
                data: foo! # inline comment
                # some other comment
                """.formatted(randomEntityName());

        var entity = parseYaml(doc);
        var initialVersion = controller.createOrUpdate(entity, doc.getBytes(UTF_8));
        var updatedDoc = new String(entityStore.getEntityDocById(initialVersion.id()).orElseThrow(), UTF_8);

        var expected = """
                id: "%s"
                createdAt: "2021-02-03T01:02:03.123456Z"
                updatedAt: "%s"
                kind: /mica/record/v1
                name: %s
                data: foo! # inline comment
                # some other comment
                """.formatted(initialVersion.id().toExternalForm(),
                objectMapper.convertValue(initialVersion.updatedAt(), String.class),
                entity.name());

        assertEquals(expected, updatedDoc);

        var nextInstant = Instant.parse("2022-02-03T01:02:03.123456Z");
        clock.setInstant(nextInstant);

        entity = parseYaml(updatedDoc);
        var updatedVersion = controller.createOrUpdate(entity, updatedDoc.getBytes(UTF_8));
        assertEquals(nextInstant, updatedVersion.updatedAt());
        updatedDoc = new String(entityStore.getEntityDocById(initialVersion.id()).orElseThrow(), UTF_8);

        expected = """
                id: "%s"
                createdAt: "2021-02-03T01:02:03.123456Z"
                updatedAt: "%s"
                kind: /mica/record/v1
                name: %s
                data: foo! # inline comment
                # some other comment
                """.formatted(updatedVersion.id().toExternalForm(),
                objectMapper.convertValue(updatedVersion.updatedAt(), String.class),
                entity.name());

        assertEquals(expected, updatedDoc);
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
