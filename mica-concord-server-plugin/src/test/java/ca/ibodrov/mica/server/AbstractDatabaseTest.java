package ca.ibodrov.mica.server;

import ca.ibodrov.mica.server.data.EntityHistoryController;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.data.InitialDataLoader;
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
        var entityHistoryController = new EntityHistoryController(dsl);
        var entityStore = new EntityStore(dsl, objectMapper, uuidGenerator, entityHistoryController);
        new InitialDataLoader(entityStore, objectMapper).load();
    }

    @AfterAll
    public static void tearDownDatabase() throws Exception {
        testDatabase.close();
    }

    protected static DSLContext dsl() {
        return testDatabase.getJooqConfiguration().dsl();
    }
}
