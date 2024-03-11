package ca.ibodrov.mica.server.ui;

import ca.ibodrov.mica.server.AbstractDatabaseTest;
import ca.ibodrov.mica.server.ui.EntityListResource.ListResponse;
import ca.ibodrov.mica.server.ui.EntityListResource.Type;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertTrue;

public class EntityListResourceTest extends AbstractDatabaseTest {

    private static final EntityListResource resource = new EntityListResource(dsl());

    @Test
    public void examplesMustBePresent() {
        var result = resource.list("/", null, null);
        assertEntry(result, "examples", Type.FOLDER);

        result = resource.list("/examples", null, null);
        assertEntry(result, "simple", Type.FOLDER);
        assertEntry(result, "hello", Type.FILE);

        result = resource.list("/examples/simple", null, null);
        assertEntry(result, "example-view", Type.FILE);

        result = resource.list("/examples/config", "/mica/view/v1", null);
        assertEntry(result, "effective-config-view", Type.FILE);
    }

    @Test
    public void searchMustReturnStuff() {
        var result = resource.list("/", null, "parametrized");
        assertEntry(result, "/examples/parametrized/example-kind", Type.FILE);
        assertEntry(result, "/examples/parametrized/example-record", Type.FILE);
        assertEntry(result, "/examples/parametrized/example-view", Type.FILE);
    }

    private static void assertEntry(ListResponse response, String name, Type type) {
        assertTrue(response.data().stream().anyMatch(e -> e.type() == type && e.name().equals(name)));
    }
}
