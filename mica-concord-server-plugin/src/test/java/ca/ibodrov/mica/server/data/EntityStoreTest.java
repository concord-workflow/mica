package ca.ibodrov.mica.server.data;

/*-
 * ~~~~~~
 * Mica
 * ------
 * Copyright (C) 2023 - 2025 Mica Authors
 * ------
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ======
 */

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EntityStoreTest {

    @Test
    public void testInplaceUpdate() {
        var doc = """
                # some comments
                createdAt: 2021-01-01T00:00:00Z
                updatedAt: 2021-01-01T00:00:00Z
                """;

        var updated = EntityStore.inplaceUpdate(doc,
                "id", "12345",
                "createdAt", "2024-01-01T00:00:00Z",
                "name", "foo");

        var expected = """
                id: "12345"
                name: "foo"
                # some comments
                createdAt: "2024-01-01T00:00:00Z"
                updatedAt: 2021-01-01T00:00:00Z
                """;
        assertEquals(expected, updated);
    }

    @Test
    public void testInplaceUpdateWhenASimilarKeyIsPresent() {
        var doc = """
                # some comments
                createdAt: 2021-01-01T00:00:00Z
                not_name: bar
                """;

        var updated = EntityStore.inplaceUpdate(doc,
                "id", "12345",
                "createdAt", "2024-01-01T00:00:00Z",
                "name", "foo");

        var expected = """
                id: "12345"
                name: "foo"
                # some comments
                createdAt: "2024-01-01T00:00:00Z"
                not_name: bar
                """;
        assertEquals(expected, updated);
    }

    @Test
    public void testInplaceUpdateOfAMissingKey() {
        var doc = """
                # some comments
                createdAt: 2021-01-01T00:00:00Z
                updatedAt: 2021-01-01T00:00:00Z
                """;

        var updated = EntityStore.inplaceUpdate(doc,
                "id", "12345",
                "name", "foo");

        var expected = """
                id: "12345"
                name: "foo"
                # some comments
                createdAt: 2021-01-01T00:00:00Z
                updatedAt: 2021-01-01T00:00:00Z
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
