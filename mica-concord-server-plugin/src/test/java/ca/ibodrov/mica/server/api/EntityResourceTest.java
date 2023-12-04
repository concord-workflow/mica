package ca.ibodrov.mica.server.api;

import ca.ibodrov.mica.api.model.EntityMetadata;
import ca.ibodrov.mica.server.AbstractDatabaseTest;
import ca.ibodrov.mica.server.UuidGenerator;
import ca.ibodrov.mica.server.data.EntityController;
import ca.ibodrov.mica.server.data.EntityKindStore;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.exceptions.ApiException;
import org.hibernate.validator.HibernateValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;

import static javax.ws.rs.core.Response.Status.CONFLICT;
import static org.junit.jupiter.api.Assertions.*;

public class EntityResourceTest extends AbstractDatabaseTest {

    private static EntityUploadResource entityUploadResource;
    private static EntityResource entityResource;

    @BeforeAll
    public static void setUp() {
        var uuidGenerator = new UuidGenerator();
        var entityStore = new EntityStore(dsl(), objectMapper, uuidGenerator);
        var entityKindStore = new EntityKindStore(entityStore, objectMapper);
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
                kind: MicaRecord/v1
                name: testSchema
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
                kind: MicaRecord/v1
                name: testRecord
                data: |
                  some
                  multiline
                  text
                """.getBytes()));
        var entities = entityResource.listEntities("testRecord", null, null, null, 10);
        assertEquals(1, entities.data().size());
        var entity1 = entities.data().get(0);
        assertEquals(entity1Version, entity1.toVersion());

        // insert another entity with a similar name and try finding them both

        var entity2Version = entityUploadResource.putYaml(new ByteArrayInputStream("""
                kind: MicaRecord/v1
                name: anotherTestRecord
                data:
                  nested:
                    object: "why not?"
                """.getBytes()));
        entities = entityResource.listEntities("testRecord", null, null, null, 10);
        assertEquals(2, entities.data().size());
        entities = entityResource.listEntities("anotherTestRecord", null, null, null, 10);
        assertEquals(1, entities.data().size());
        var entity2 = entities.data().get(0);
        assertEquals(entity2Version, entity2.toVersion());
    }

    @Test
    public void testPutAndGetAsYaml() {
        var entityVersion = entityUploadResource.putYaml(new ByteArrayInputStream("""
                kind: MicaRecord/v1
                name: yamlRecord
                # comments are ignored
                data:
                  x: |
                    multi
                    line
                    text
                """.getBytes()));

        var response = entityResource.getEntityAsYamlString(entityVersion.id());
        assertEquals(200, response.getStatus());

        var expectedYaml = """
                id: %s
                name: yamlRecord
                kind: MicaRecord/v1
                createdAt: %s
                updatedAt: %s
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
                kind: MicaRecord/v1
                name: someRecord
                data: "foo"
                """.getBytes()));

        var entityList = entityResource.listEntities(null, "someRecord", null, null, 10);
        assertTrue(entityList.data().stream().map(EntityMetadata::toVersion).anyMatch(createdVersion::equals));

        var deletedVersion = entityResource.deleteById(createdVersion.id().id());
        assertEquals(createdVersion, deletedVersion);

        entityList = entityResource.listEntities(null, "someRecord", null, null, 10);
        assertTrue(entityList.data().stream().map(EntityMetadata::toVersion).noneMatch(createdVersion::equals));
    }

    @Test
    public void testValidation() {
        // name too short (2 characters)
        assertThrows(ConstraintViolationException.class,
                () -> entityUploadResource.putYaml(new ByteArrayInputStream("""
                        kind: MicaRecord/v1
                        name: fo
                        # comments are ignored
                        data:
                          x: |
                            multi
                            line
                            text
                        """.getBytes())));

        // name too long (257 characters)
        assertThrows(ConstraintViolationException.class,
                () -> entityUploadResource.putYaml(new ByteArrayInputStream(
                        """
                                kind: MicaRecord/v1
                                name: foooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo
                                # comments are ignored
                                data:
                                  x: |
                                    multi
                                    line
                                    text
                                """
                                .getBytes())));

        // not a valid name (starts with a digit)
        assertThrows(ConstraintViolationException.class,
                () -> entityUploadResource.putYaml(new ByteArrayInputStream("""
                        kind: MicaRecord/v1
                        name: /foobar/
                        # comments are ignored
                        data:
                          x: |
                            multi
                            line
                            text
                        """.getBytes())));
    }

    private String format(OffsetDateTime v) {
        return objectMapper.convertValue(v, String.class);
    }
}
