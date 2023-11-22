package ca.ibodrov.mica.server.api.resources;

import ca.ibodrov.mica.server.UuidGenerator;
import ca.ibodrov.mica.server.api.ApiException;
import ca.ibodrov.mica.server.data.EntityController;
import ca.ibodrov.mica.testing.TestDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.hibernate.validator.HibernateValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.validation.Validation;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;

import static javax.ws.rs.core.Response.Status.CONFLICT;
import static org.junit.jupiter.api.Assertions.*;

public class EntityResourceTest {

    private static TestDatabase testDatabase;
    private static ObjectMapper objectMapper;
    private static EntityResource entityResource;

    @BeforeAll
    public static void setUp() {
        testDatabase = new TestDatabase();
        testDatabase.start();

        objectMapper = new ObjectMapperProvider().get();

        var dsl = testDatabase.getJooqConfiguration().dsl();
        var uuidGenerator = new UuidGenerator();
        var controller = new EntityController(dsl, uuidGenerator);
        var validator = Validation.byProvider(HibernateValidator.class)
                .configure()
                .buildValidatorFactory()
                .getValidator();
        entityResource = new EntityResource(dsl, controller, objectMapper, validator);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        testDatabase.close();
    }

    @Test
    public void testRePutSameYaml() {
        // insert the initial version

        var yaml = """
                kind: MicaSchema/v1
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
        var result1 = entityResource.putYaml(new ByteArrayInputStream(yaml.getBytes()));
        assertNotNull(result1.id());
        assertNotNull(result1.updatedAt());

        // update the same version

        var yamlWithId = "id: %s\nupdatedAt: %s\n%s".formatted(result1.id().toExternalForm(), result1.updatedAt(),
                yaml);
        var result2 = entityResource.putYaml(new ByteArrayInputStream(yamlWithId.getBytes()));
        assertNotNull(result2.id());
        assertNotNull(result2.updatedAt());
        assertEquals(result1.id(), result2.id());
        assertTrue(result1.updatedAt().isBefore(result2.updatedAt()));

        // try updating a stale version

        var error = assertThrows(ApiException.class,
                () -> entityResource.putYaml(new ByteArrayInputStream(yamlWithId.getBytes())));
        assertEquals(CONFLICT, error.getStatus());
    }

    @Test
    public void testList() {
        // insert an entity

        var entity1Version = entityResource.putYaml(new ByteArrayInputStream("""
                kind: MicaRecord/v1
                name: testRecord
                data: |
                  some
                  multiline
                  text
                """.getBytes()));
        var entities = entityResource.listEntities("testRecord");
        assertEquals(1, entities.data().size());
        var entity1 = entities.data().get(0);
        assertEquals(entity1Version, entity1.toVersion());

        // insert another entity with a similar name and try finding them both

        var entity2Version = entityResource.putYaml(new ByteArrayInputStream("""
                kind: MicaRecord/v1
                name: anotherTestRecord
                data:
                  nested:
                    object: "why not?"
                """.getBytes()));
        entities = entityResource.listEntities("testRecord");
        assertEquals(2, entities.data().size());
        entities = entityResource.listEntities("anotherTestRecord");
        assertEquals(1, entities.data().size());
        var entity2 = entities.data().get(0);
        assertEquals(entity2Version, entity2.toVersion());
    }

    @Test
    public void testPutAndGetAsYaml() {
        var entityVersion = entityResource.putYaml(new ByteArrayInputStream("""
                kind: MicaRecord/v1
                name: yamlRecord
                # comments are ignored
                data:
                  x: |
                    multi
                    line
                    text
                """.getBytes()));

        var response = entityResource.getEntityAsYamlString(entityVersion.id().id());
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

    private String format(OffsetDateTime v) {
        return objectMapper.convertValue(v, String.class);
    }
}
