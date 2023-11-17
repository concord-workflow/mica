package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.schema.ObjectSchemaNode;
import ca.ibodrov.mica.server.TestData;
import ca.ibodrov.mica.server.TestDatabase;
import ca.ibodrov.mica.server.TestObjectMapperProvider;
import ca.ibodrov.mica.server.UuidGenerator;
import ca.ibodrov.mica.server.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static ca.ibodrov.mica.schema.StandardTypes.OBJECT_TYPE;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClientEndpointControllerTest {

    private static TestDatabase testDatabase;

    private ObjectMapper objectMapper;
    private ClientEndpointController controller;

    @BeforeAll
    public static void setUp() {
        testDatabase = new TestDatabase();
        testDatabase.start();
    }

    @BeforeEach
    public void init() {
        objectMapper = new TestObjectMapperProvider().get();
        controller = new ClientEndpointController(testDatabase.getJooqConfiguration(), objectMapper,
                new UuidGenerator());
    }

    @AfterAll
    public static void tearDown() throws Exception {
        testDatabase.close();
    }

    @Test
    public void testImportFromClientDataButProfileIsMissing() {
        assertThrows(ApiException.class, () -> controller.importFromClientData(UUID.randomUUID()));
    }

    @Test
    public void testSimpleImportFromClientData() {
        var dsl = testDatabase.getJooqConfiguration().dsl();
        var profileId = UUID.randomUUID();

        dsl.transaction(tx -> {
            TestData.insertProfile(tx, profileId, "foobar",
                    toJson(new ObjectSchemaNode(Optional.of(OBJECT_TYPE),
                            Optional.of(Map.of("endpointUri", ObjectSchemaNode.string())),
                            Optional.of(Set.of("endpointUri")))));

            TestData.insertClient(tx, UUID.randomUUID(), "acme");

            TestData.insertClientData(tx, UUID.randomUUID(), "acme",
                    toJson(Map.of("endpointUri", "http://localhost:8001/api/v1/server/ping")));
        });

        controller.importFromClientData(profileId);
    }

    private String toJson(Object o) {
        try {
            return new TestObjectMapperProvider().get().writeValueAsString(o);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
