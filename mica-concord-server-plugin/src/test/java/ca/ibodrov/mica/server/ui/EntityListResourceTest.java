package ca.ibodrov.mica.server.ui;

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

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.AbstractDatabaseTest;
import ca.ibodrov.mica.server.ui.EntityListResource.ListResponse;
import ca.ibodrov.mica.server.ui.EntityListResource.Type;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class EntityListResourceTest extends AbstractDatabaseTest {

    private static final EntityListResource resource = new EntityListResource(dsl());

    @Test
    public void examplesMustBePresent() {
        var result = resource.list("/", null, null, false);
        assertEntry(result, "examples", Type.FOLDER);

        result = resource.list("/examples", null, null, false);
        assertEntry(result, "simple", Type.FOLDER);
        assertEntry(result, "hello", Type.FILE);

        result = resource.list("/examples/simple", null, null, false);
        assertEntry(result, "example-view", Type.FILE);

        result = resource.list("/examples/config", "/mica/view/v1", null, false);
        assertEntry(result, "effective-config-view", Type.FILE);
    }

    @Test
    public void searchMustReturnStuff() {
        var result = resource.list("/", null, "parametrized", false);
        assertEntry(result, "/examples/parametrized/example-kind", Type.FILE);
        assertEntry(result, "/examples/parametrized/example-record", Type.FILE);
        assertEntry(result, "/examples/parametrized/example-view", Type.FILE);
    }

    @Test
    public void canBeDeletedWorksAsIntended() {
        var entityName = "/test-canBeDeletedWorksAsIntended";

        var initialVersion = dsl()
                .transactionResult(
                        tx -> entityStore.upsert(tx.dsl(),
                                PartialEntity.create(entityName, "/mica/record/v1",
                                        Map.of("data", TextNode.valueOf("Hi!"))),
                                null))
                .orElseThrow();

        var canBeDeleted = resource.canBeDeleted(initialVersion.id());
        assertTrue(canBeDeleted.canBeDeleted());

        var deletedVersion = dsl().transactionResult(tx -> entityStore.deleteById(tx.dsl(), initialVersion.id()))
                .orElseThrow();
        assertEquals(initialVersion.id(), deletedVersion.id());

        canBeDeleted = resource.canBeDeleted(initialVersion.id());
        assertFalse(canBeDeleted.canBeDeleted());

        var newVersion = dsl()
                .transactionResult(
                        tx -> entityStore.upsert(tx.dsl(),
                                PartialEntity.create(entityName, "/mica/record/v1",
                                        Map.of("data", TextNode.valueOf("Hi!"))),
                                null))
                .orElseThrow();

        canBeDeleted = resource.canBeDeleted(newVersion.id());
        assertTrue(canBeDeleted.canBeDeleted());
    }

    private static void assertEntry(ListResponse response, String name, Type type) {
        assertTrue(response.data().stream().anyMatch(e -> e.type() == type && e.name().equals(name)));
    }
}
