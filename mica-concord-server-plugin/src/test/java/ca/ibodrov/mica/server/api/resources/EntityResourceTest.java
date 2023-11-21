package ca.ibodrov.mica.server.api.resources;

import ca.ibodrov.mica.server.UuidGenerator;
import ca.ibodrov.mica.server.api.ApiException;
import ca.ibodrov.mica.server.data.EntityController;
import ca.ibodrov.mica.testing.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static javax.ws.rs.core.Response.Status.CONFLICT;
import static org.junit.jupiter.api.Assertions.*;

public class EntityResourceTest {

    private static TestDatabase testDatabase;
    private static EntityResource entityResource;

    @BeforeAll
    public static void setUp() {
        testDatabase = new TestDatabase();
        testDatabase.start();

        var uuidGenerator = new UuidGenerator();
        entityResource = new EntityResource(
                new EntityController(testDatabase.getJooqConfiguration().dsl(), uuidGenerator));
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
        assertNotNull(result1.entityId());
        assertNotNull(result1.updatedAt());

        // update the same version

        var yamlWithId = "id: %s\nupdatedAt: %s\n%s".formatted(result1.entityId().toExternalForm(), result1.updatedAt(),
                yaml);
        var result2 = entityResource.putYaml(new ByteArrayInputStream(yamlWithId.getBytes()));
        assertNotNull(result2.entityId());
        assertNotNull(result2.updatedAt());
        assertEquals(result1.entityId(), result2.entityId());
        assertTrue(result1.updatedAt().isBefore(result2.updatedAt()));

        // try updating a stale version

        var error = assertThrows(ApiException.class,
                () -> entityResource.putYaml(new ByteArrayInputStream(yamlWithId.getBytes())));
        assertEquals(CONFLICT, error.getStatus());
    }
}
