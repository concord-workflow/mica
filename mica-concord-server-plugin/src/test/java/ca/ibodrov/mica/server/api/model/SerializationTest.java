package ca.ibodrov.mica.server.api.model;

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

import ca.ibodrov.mica.api.model.EntityId;
import ca.ibodrov.mica.api.model.PartialEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SerializationTest {

    private final ObjectMapper mapper = new ObjectMapperProvider().get();

    @Test
    public void testIdWrappers() {
        var entityId1 = new EntityId(UUID.randomUUID());
        var result = parseJson(EntityIdContainer.class, """
                { "id": "%s" }
                """.formatted(entityId1.toExternalForm()));
        assertEquals(entityId1, result.id());
    }

    @Test
    public void testEmptyJsonNodes() {
        var result = parseJson(OptionalJsonNodeContainer.class, """
                {}
                """);
        assertEquals(Optional.of(NullNode.getInstance()), result.node());
    }

    @Test
    public void testAnySetterInPartialEntities() {
        var result = parseJson(PartialEntity.class, """
                {
                  "name": "test",
                  "kind": "test",
                  "foo": "bar"
                }
                """);
        assertEquals("bar", result.data().get("foo").asText());

        var json = toJson(result);
        assertEquals("{\"name\":\"test\",\"kind\":\"test\",\"foo\":\"bar\"}", json);

        result = parseJson(PartialEntity.class, """
                {
                  "name": "test",
                  "kind": "test",
                  "foo": {
                    "bar": "baz"
                  }
                }
                """);
        assertEquals("baz", result.data().get("foo").get("bar").asText());

        json = toJson(result);
        assertEquals("{\"name\":\"test\",\"kind\":\"test\",\"foo\":{\"bar\":\"baz\"}}", json);

        result = parseJson(PartialEntity.class, """
                {
                  "name": "test",
                  "kind": "test",
                  "data": {
                    "foo": "bar"
                  }
                }
                """);
        assertEquals("bar", result.data().get("data").get("foo").asText());

        json = toJson(result);
        assertEquals("{\"name\":\"test\",\"kind\":\"test\",\"data\":{\"foo\":\"bar\"}}", json);
    }

    @Test
    public void testAnySetterInEntities() {
        var result = parseJson(PartialEntity.class, """
                {
                  "id": "00000000-0000-0000-0000-000000000000",
                  "name": "test",
                  "kind": "test",
                  "createdAt": "2023-01-01T00:00:00Z",
                  "updatedAt": "2023-01-01T00:00:00Z",
                  "data": {
                    "foo": "bar"
                  }
                }
                """);
        assertEquals("bar", result.data().get("data").get("foo").asText());

        var json = toJson(result);
        assertEquals(
                "{\"id\":\"00000000-0000-0000-0000-000000000000\",\"name\":\"test\",\"kind\":\"test\",\"createdAt\":\"2023-01-01T00:00:00Z\",\"updatedAt\":\"2023-01-01T00:00:00Z\",\"data\":{\"foo\":\"bar\"}}",
                json);
    }

    private <T> T parseJson(Class<T> klass, @Language("JSON") String json) {
        try {
            return mapper.readValue(json, klass);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public record EntityIdContainer(EntityId id) {
    }

    public record OptionalJsonNodeContainer(Optional<JsonNode> node) {
    }
}
