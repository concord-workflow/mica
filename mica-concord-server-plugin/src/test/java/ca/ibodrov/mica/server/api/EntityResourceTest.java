package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.EntityMetadata;
import ca.ibodrov.mica.server.AbstractDatabaseTest;
import ca.ibodrov.mica.server.UuidGenerator;
import ca.ibodrov.mica.server.data.BuiltinSchemas;
import ca.ibodrov.mica.server.data.EntityController;
import ca.ibodrov.mica.server.data.EntityKindStore;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.exceptions.ApiException;
import com.walmartlabs.concord.common.DateTimeUtils;
import org.hibernate.validator.HibernateValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import static java.time.ZoneOffset.UTC;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static org.junit.jupiter.api.Assertions.*;

public class EntityResourceTest extends AbstractDatabaseTest {

    private static EntityUploadResource entityUploadResource;
    private static EntityResource entityResource;

    @BeforeAll
    public static void setUp() {
        var uuidGenerator = new UuidGenerator();
        var entityStore = new EntityStore(dsl(), objectMapper, uuidGenerator);
        var builtInSchemas = new BuiltinSchemas(objectMapper);
        var entityKindStore = new EntityKindStore(entityStore, builtInSchemas, objectMapper);
        var controller = new EntityController(entityStore, entityKindStore, objectMapper);
        var validator = Validation.byProvider(HibernateValidator.class)
                .configure()
                .buildValidatorFactory()
                .getValidator();

        entityResource = new EntityResource(entityStore, objectMapper);
        entityUploadResource = new EntityUploadResource(controller, validator, objectMapper);
    }

    @Test
    public void testRePutSameYaml() {
        // insert the initial version

        var yaml = """
                kind: /mica/record/v1
                name: /testSchema
                data:
                  type: object
                  properties:
                    - name: id
                      type: string
                      required: true
                    - name: name
                      type: string
                      required: true
                """;
        var result1 = entityUploadResource.putYaml(new ByteArrayInputStream(yaml.getBytes()));
        assertNotNull(result1.id());
        assertNotNull(result1.updatedAt());

        // update the same version

        var yamlWithId = "id: %s\nupdatedAt: %s\n%s".formatted(result1.id().toExternalForm(), result1.updatedAt(),
                yaml);
        var result2 = entityUploadResource.putYaml(new ByteArrayInputStream(yamlWithId.getBytes()));
        assertNotNull(result2.id());
        assertNotNull(result2.updatedAt());
        assertEquals(result1.id(), result2.id());
        assertTrue(result1.updatedAt().isBefore(result2.updatedAt()));

        // try updating a stale version

        var error = assertThrows(ApiException.class,
                () -> entityUploadResource.putYaml(new ByteArrayInputStream(yamlWithId.getBytes())));
        assertEquals(CONFLICT, error.getStatus());
    }

    @Test
    public void testList() {
        // insert an entity

        var entity1Version = entityUploadResource.putYaml(new ByteArrayInputStream("""
                kind: /mica/record/v1
                name: /testRecord
                data: |
                  some
                  multiline
                  text
                """.getBytes()));
        var entities = entityResource.listEntities("testRecord", null, null, null, null, 10);
        assertEquals(1, entities.data().size());
        var entity1 = entities.data().get(0);
        assertEquals(entity1Version, entity1.toVersion());

        // insert another entity with a similar name and try finding them both

        var entity2Version = entityUploadResource.putYaml(new ByteArrayInputStream("""
                kind: /mica/record/v1
                name: /anotherTestRecord
                data:
                  nested:
                    object: "why not?"
                """.getBytes()));
        entities = entityResource.listEntities("testRecord", null, null, null, null, 10);
        assertEquals(2, entities.data().size());
        entities = entityResource.listEntities("anotherTestRecord", null, null, null, null, 10);
        assertEquals(1, entities.data().size());
        var entity2 = entities.data().get(0);
        assertEquals(entity2Version, entity2.toVersion());
    }

    @Test
    public void testPutAndGetAsYaml() {
        var entityVersion = entityUploadResource.putYaml(new ByteArrayInputStream("""
                kind: /mica/record/v1
                name: /yamlRecord
                # comments are ignored unless the doc is saved with the entity
                data:
                  x: |
                    multi
                    line
                    text
                """.getBytes()));

        var response = entityResource.getEntityAsYamlString(entityVersion.id(), null);
        assertEquals(200, response.getStatus());

        var expectedYaml = """
                id: "%s"
                name: "/yamlRecord"
                kind: "/mica/record/v1"
                createdAt: "%s"
                updatedAt: "%s"
                data:
                  x: |
                    multi
                    line
                    text
                """.formatted(entityVersion.id().toExternalForm(), format(entityVersion.updatedAt()),
                format(entityVersion.updatedAt()));
        assertEquals(expectedYaml, response.getEntity());
    }

    @Test
    public void testPutListDelete() {
        var createdVersion = entityUploadResource.putYaml(new ByteArrayInputStream("""
                kind: /mica/record/v1
                name: /someRecord
                data: "foo"
                """.getBytes()));

        var entityList = entityResource.listEntities(null, null, "/someRecord", null, null, 10);
        assertTrue(entityList.data().stream().map(EntityMetadata::toVersion).anyMatch(createdVersion::equals));

        var deletedVersion = entityResource.deleteById(createdVersion.id().id());
        assertEquals(createdVersion, deletedVersion);

        entityList = entityResource.listEntities(null, null, "/someRecord", null, null, 10);
        assertTrue(entityList.data().stream().map(EntityMetadata::toVersion).noneMatch(createdVersion::equals));
    }

    @Test
    public void testValidation() {
        // name too short (2 characters)
        assertThrows(ConstraintViolationException.class,
                () -> entityUploadResource.putYaml(new ByteArrayInputStream("""
                        kind: /mica/record/v1
                        name: /f
                        # comments are ignored
                        data:
                          x: |
                            multi
                            line
                            text
                        """.getBytes())));

        // name too long (1025 characters)
        assertThrows(ConstraintViolationException.class,
                () -> entityUploadResource.putYaml(new ByteArrayInputStream(
                        """
                                kind: /mica/record/v1
                                name: %s
                                # comments are ignored
                                data:
                                  x: |
                                    multi
                                    line
                                    text
                                """
                                .formatted(randomEntityName(1025))
                                .getBytes())));

        // not a valid name (doesn't start with a forward slash)
        assertThrows(ConstraintViolationException.class,
                () -> entityUploadResource.putYaml(new ByteArrayInputStream("""
                        kind: /mica/record/v1
                        name: foobar/
                        # comments are ignored
                        data:
                          x: |
                            multi
                            line
                            text
                        """.getBytes())));
    }

    @Test
    public void testGetByIdAndUpdatedAt() {
        var version1 = entityUploadResource.putYaml(new ByteArrayInputStream("""
                kind: /mica/record/v1
                name: /testGetByIdAndUpdatedAt/a
                data: aaa
                """.getBytes()));

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        var version2 = entityUploadResource.putYaml(new ByteArrayInputStream("""
                id: "%s"
                updatedAt: "%s"
                kind: /mica/record/v1
                name: /testGetByIdAndUpdatedAt/a
                data: bbb
                """
                .formatted(version1.id().toExternalForm(),
                        objectMapper.convertValue(version1.updatedAt(), String.class))
                .getBytes()));

        assertEquals(version1.id(), version2.id());
        assertNotEquals(version1.updatedAt(), version2.updatedAt());

        var error = assertThrows(ApiException.class, () -> entityResource.getEntityById(version1.id(),
                DateTimeUtils.toIsoString(version1.updatedAt().atOffset(UTC))));
        assertEquals(404, error.getStatus().getStatusCode());

        var entity = entityResource.getEntityById(version1.id(),
                DateTimeUtils.toIsoString(version2.updatedAt().atOffset(UTC)));
        assertEquals(version2.id(), entity.id());
        assertEquals(version2.updatedAt(), entity.updatedAt());
        assertEquals("/testGetByIdAndUpdatedAt/a", entity.name());
    }

    private String format(Instant v) {
        return objectMapper.convertValue(v, String.class);
    }

    private static String randomEntityName(int length) {
        return "/test/" + ThreadLocalRandom.current()
                .ints(length - 6, 'a', 'z' + 1)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
