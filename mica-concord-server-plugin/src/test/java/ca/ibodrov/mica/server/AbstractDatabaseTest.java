package ca.ibodrov.mica.server;

import ca.ibodrov.mica.server.data.EntityKindStore;
import ca.ibodrov.mica.server.data.EntityStore;
import ca.ibodrov.mica.server.data.InitialDataLoader;
import ca.ibodrov.mica.testing.TestDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.sonatype.siesta.jackson2.ObjectMapperProvider;

public class AbstractDatabaseTest {

    private static TestDatabase testDatabase;
    protected static ObjectMapper objectMapper;
    protected static UuidGenerator uuidGenerator;

    @BeforeAll
    public static void setUpDatabase() {
        testDatabase = new TestDatabase();
        testDatabase.start();

        objectMapper = new ObjectMapperProvider().get()
                .registerModule(new JavaTimeModule())
                .registerModule(new Jdk8Module())
                .registerModule(new GuavaModule());

        uuidGenerator = new UuidGenerator();

        var dsl = testDatabase.getJooqConfiguration().dsl();
        var entityStore = new EntityStore(dsl, objectMapper, uuidGenerator);
        var entityKindStore = new EntityKindStore(entityStore, objectMapper);
        new InitialDataLoader(entityKindStore, objectMapper).load();
    }

    @AfterAll
    public static void tearDownDatabase() throws Exception {
        testDatabase.close();
    }

    protected static DSLContext dsl() {
        return testDatabase.getJooqConfiguration().dsl();
    }
}
