package ca.ibodrov.mica.server.api.resources;

import ca.ibodrov.mica.testing.TestData;
import ca.ibodrov.mica.testing.TestDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClientResourceTest {

    private static TestDatabase testDatabase;

    @BeforeAll
    public static void setUp() {
        testDatabase = new TestDatabase();
        testDatabase.start();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        testDatabase.close();
    }

    @Test
    public void testListClients() {
        var dsl = testDatabase.getJooqConfiguration().dsl();
        dsl.transaction(tx -> {
            TestData.insertClient(tx, UUID.randomUUID(), "foobar");
            TestData.insertClientData(tx, UUID.randomUUID(), "foobar", "{\"existingProp\": 123}");

            TestData.insertClient(tx, UUID.randomUUID(), "barbaz");
            TestData.insertClientData(tx, UUID.randomUUID(), "barbaz", "{\"existingProp\": 456}");
        });

        var objectMapper = new ObjectMapper();
        var resource = new ClientResource(dsl, objectMapper);

        var result = resource.listClients("foo", Set.of("nonExistingProp"));
        assertEquals(1, result.data().size());
        assertEquals(0, result.data().get(0).properties().size());

        result = resource.listClients("bar", Set.of("nonExistingProp"));
        assertEquals(2, result.data().size());
        assertTrue(result.data().get(0).properties().isEmpty());
        assertTrue(result.data().get(1).properties().isEmpty());

        result = resource.listClients("foo", Set.of("existingProp"));
        assertEquals(1, result.data().size());
        assertEquals(1, result.data().get(0).properties().size());
        assertEquals(123, result.data().get(0).properties().get("existingProp"));

        result = resource.listClients("bar", Set.of("existingProp"));
        assertEquals(2, result.data().size());
        assertEquals(1, result.data().get(0).properties().size());
        assertEquals(123, result.data().get(0).properties().get("existingProp"));
        assertEquals(1, result.data().get(1).properties().size());
        assertEquals(456, result.data().get(1).properties().get("existingProp"));
    }
}
