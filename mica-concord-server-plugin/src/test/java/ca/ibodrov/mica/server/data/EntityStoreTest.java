package ca.ibodrov.mica.server.data;

import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EntityStoreTest {

    @Test
    public void testInplaceUpdate() {
        var doc = """
                # some comments
                createdAt: 2021-01-01T00:00:00Z
                updatedAt: 2021-01-01T00:00:00Z
                name: bar
                """;

        var updated = EntityStore.inplaceUpdate(doc,
                "id", "12345",
                "createdAt", "2024-01-01T00:00:00Z");

        var expected = """
                id: "12345"
                # some comments
                createdAt: "2024-01-01T00:00:00Z"
                updatedAt: 2021-01-01T00:00:00Z
                name: bar
                """;
        assertEquals(expected, updated);
    }

    @Test
    public void testNameNormalization() {
        assertEquals("/test", EntityStore.normalizeName("/test"));
        assertEquals("/test", EntityStore.normalizeName("//test"));
        assertEquals("/test", EntityStore.normalizeName("///test"));
        assertEquals("/test/foo/bar", EntityStore.normalizeName("/test//foo/bar"));
        assertEquals("/test/foo/bar", EntityStore.normalizeName("/test//foo///bar"));
    }
}
