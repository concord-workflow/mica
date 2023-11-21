package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.ClientDataDocument;
import ca.ibodrov.mica.api.model.ClientDataEntry;
import ca.ibodrov.mica.api.model.Document;
import ca.ibodrov.mica.server.TestDatabase;
import ca.ibodrov.mica.server.UuidGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ClientDataImporterTest {

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
    public void testImport() {
        var document = new Document(Optional.of(ClientDataDocument.KIND),
                Map.of("clients",
                        List.of(
                                new ClientDataEntry("id1", Map.of()),
                                new ClientDataEntry("id2", Map.of()))));

        var importer = new ClientDataImporter(testDatabase.getJooqConfiguration().dsl(), new UuidGenerator());
        importer.importDocument(document);
    }
}
