package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.AbstractDatabaseTest;
import ca.ibodrov.mica.server.YamlMapper;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static ca.ibodrov.mica.server.data.UserEntryUtils.user;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static org.junit.jupiter.api.Assertions.*;

public class EntityControllerTest extends AbstractDatabaseTest {

    private static final UserPrincipal session = new UserPrincipal("test", user("test"));
    private static YamlMapper yamlMapper;
    private static EntityController controller;
    private static EntityStore entityStore;

    @BeforeAll
    public static void setUp() {
        yamlMapper = new YamlMapper(objectMapper);
        var entityHistoryController = new EntityHistoryController(dsl());
        entityStore = new EntityStore(dsl(), objectMapper, uuidGenerator, entityHistoryController);
        var builtinSchemas = new BuiltinSchemas(objectMapper);
        var entityKindStore = new EntityKindStore(entityStore, builtinSchemas);
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

        var error = assertThrows(ApiException.class, () -> controller.createOrUpdate(session, parseYaml(yaml)));
        assertEquals(BAD_REQUEST, error.getStatus());
    }

    @Test
    public void testUploadBuiltInEntityKinds() {
        controller.createOrUpdate(session, parseYaml("""
                kind: /mica/record/v1
                name: %s
                data: |
                  some text
                """.formatted(randomEntityName())));

        controller.createOrUpdate(session, parseYaml("""
                kind: /mica/kind/v1
                name: %s
                schema:
                  type: object
                  properties:
                    foo:
                      type: string
                """.formatted(randomEntityName())));

        controller.createOrUpdate(session, parseYaml("""
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
        var error1 = assertThrows(ValidationErrorsException.class, () -> controller.createOrUpdate(session, entity1));
        assertEquals(1, error1.getValidationErrors().size());
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
        var initialVersion = controller.createOrUpdate(session, entity, doc.getBytes(UTF_8), false);
        var createdAt = initialVersion.updatedAt();
        var updatedDoc = new String(entityStore.getEntityDocById(initialVersion.id(), null).orElseThrow(), UTF_8);

        var expected = """
                id: "%s"
                createdAt: "%s"
                updatedAt: "%s"
                kind: /mica/record/v1
                name: %s
                data: foo! # inline comment
                # some other comment
                """.formatted(initialVersion.id().toExternalForm(),
                objectMapper.convertValue(createdAt, String.class),
                objectMapper.convertValue(initialVersion.updatedAt(), String.class),
                entity.name());

        assertEquals(expected, updatedDoc);

        entity = parseYaml(updatedDoc);
        var updatedVersion = controller.createOrUpdate(session, entity, updatedDoc.getBytes(UTF_8), false);
        updatedDoc = new String(entityStore.getEntityDocById(initialVersion.id(), null).orElseThrow(), UTF_8);

        expected = """
                id: "%s"
                createdAt: "%s"
                updatedAt: "%s"
                kind: /mica/record/v1
                name: %s
                data: foo! # inline comment
                # some other comment
                """.formatted(updatedVersion.id().toExternalForm(),
                objectMapper.convertValue(createdAt, String.class),
                objectMapper.convertValue(updatedVersion.updatedAt(), String.class),
                entity.name());

        assertEquals(expected, updatedDoc);
    }

    @Test
    public void usersCanOverwriteConflicts() {
        // create the initial version

        var doc = """
                kind: /mica/record/v1
                name: %s
                data: foo! # inline comment
                # some other comment
                """.formatted(randomEntityName());

        var entity = parseYaml(doc);
        var initialVersion = controller.createOrUpdate(session, entity, doc.getBytes(UTF_8), false);

        // fetch the created document

        var createdDoc = entityStore.getEntityDocById(initialVersion.id(), initialVersion.updatedAt())
                .map(bytes -> new String(bytes, UTF_8))
                .orElseThrow();

        // modify and update the document as if it was done by a user
        var updatedDoc = createdDoc + "\n # updated by user1";
        var updatedVersion = controller.createOrUpdate(session, parseYaml(updatedDoc), updatedDoc.getBytes(UTF_8),
                false);
        assertEquals(initialVersion.id(), updatedVersion.id());
        assertNotEquals(initialVersion.updatedAt(), updatedVersion.updatedAt());

        // modify and try updating the same original document as if it was done by
        // another user
        var updatedDocAlternative = createdDoc + "\n # updated by user2";
        var error = assertThrows(ApiException.class,
                () -> controller.createOrUpdate(session, parseYaml(updatedDocAlternative),
                        updatedDocAlternative.getBytes(UTF_8),
                        false));
        assertEquals(CONFLICT, error.getStatus());

        // overwrite the document
        var overwrittenVersion = controller.createOrUpdate(session, parseYaml(updatedDocAlternative),
                updatedDocAlternative.getBytes(UTF_8), true);
        assertEquals(initialVersion.id(), overwrittenVersion.id());
        assertNotEquals(initialVersion.updatedAt(), overwrittenVersion.updatedAt());
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
