package ca.ibodrov.mica.server;

import ca.ibodrov.mica.server.data.BuiltinSchemas;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.data.InitialDataLoader;
import ca.ibodrov.mica.testing.TestDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class AbstractDatabaseTest {

    private static TestDatabase testDatabase;
    protected static ObjectMapper objectMapper;
    protected static UuidGenerator uuidGenerator;

    @BeforeAll
    public static void setUpDatabase() {
        testDatabase = new TestDatabase();
        testDatabase.start();

        objectMapper = new ObjectMapperProvider().get();

        uuidGenerator = new UuidGenerator();

        var dsl = testDatabase.getJooqConfiguration().dsl();
        var entityStore = new EntityStore(dsl, objectMapper, uuidGenerator);
        var builtinSchemas = new BuiltinSchemas(objectMapper);
        new InitialDataLoader(builtinSchemas, entityStore, objectMapper).load();
    }

    @AfterAll
    public static void tearDownDatabase() throws Exception {
        testDatabase.close();
    }

    protected static DSLContext dsl() {
        return testDatabase.getJooqConfiguration().dsl();
    }
}
