package ca.ibodrov.mica.server.reports;

import ca.ibodrov.mica.server.AbstractDatabaseTest;
import ca.ibodrov.mica.server.data.AllEntityFetchers;
import ca.ibodrov.mica.server.data.EntityFetchers;
import ca.ibodrov.mica.server.data.EntityKindStore;
import ca.ibodrov.mica.server.data.InternalEntityFetcher;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ReportTest extends AbstractDatabaseTest {

    private static final EntityKindStore entityKindStore = new EntityKindStore(entityStore);
    private static final EntityFetchers entityFetchers = new AllEntityFetchers(
            Set.of(new InternalEntityFetcher(dsl(), objectMapper)));

    @Test
    public void runValidateAllReportOnInitialData() {
        var report = new ValidateAllReport(entityKindStore, entityFetchers, objectMapper);
        var options = new ValidateAllReport.Options(true);
        var result = report.run(options).data().get("report");
        assertNotNull(result);
        assertTrue(result.isArray());
        // we expect no violations in our initial data (including examples)
        // if that's no longer true (we want "broken" examples), this test will fail
        // and needs to be updated
        assertEquals(0, result.size());
    }
}
