package ca.ibodrov.mica.server.data;

import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class EntityStoreTest {

    @Test
    public void testInplaceUpdate() {
        var doc = """
                # some comments
                createdAt: 2021-01-01T00:00:00Z
                updatedAt: 2021-01-01T00:00:00Z
                name: bar
                """
                .getBytes(UTF_8);

        var updated = EntityStore.inplaceUpdate(doc,
                "id", "12345",
                "createdAt", "2024-01-01T00:00:00Z");

        var expected = """
                id: "12345"
                # some comments
                createdAt: "2024-01-01T00:00:00Z"
                updatedAt: 2021-01-01T00:00:00Z
                name: bar
                """
                .getBytes(UTF_8);
        assertArrayEquals(expected, updated);
    }
}
