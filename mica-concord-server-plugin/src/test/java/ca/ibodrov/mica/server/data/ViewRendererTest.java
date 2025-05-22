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

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.api.model.ViewLike;
import ca.ibodrov.mica.server.YamlMapper;
import ca.ibodrov.mica.server.data.Validator.NoopSchemaFetcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static ca.ibodrov.mica.server.data.BuiltinSchemas.asViewLike;
import static org.junit.jupiter.api.Assertions.*;

public class ViewRendererTest {

    private static final ObjectMapper objectMapper = new ObjectMapperProvider().get();
    private static final YamlMapper yamlMapper = new YamlMapper(objectMapper);
    private static final JsonPathEvaluator jsonPathEvaluator = new JsonPathEvaluator(objectMapper);
    private static final ViewRenderer renderer = new ViewRenderer(jsonPathEvaluator, objectMapper);
    private static final ViewInterpolator interpolator = new ViewInterpolator(objectMapper, new NoopSchemaFetcher());

    @Test
    public void simpleExample() {
        var view = parseView("""
                kind: /mica/view/v1
                name: %s
                selector:
                  entityKind: %s
                data:
                  jsonPath: %s
                  flatten: %s
                """.formatted("test", "/mica/record/v1", "$.data", false));

        var entityA = parseYaml("""
                kind: /mica/record/v1
                name: A
                data: Some data from A
                """);

        var entityB = parseYaml("""
                kind: /mica/record/v1
                name: B
                data: Some data from B
                """);

        var result = renderer.render(view, Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals("Some data from A", result.data().get(0).asText());
        assertEquals("Some data from B", result.data().get(1).asText());
    }

    @Test
    public void complexExample() {
        var entityA = parseYaml("""
                kind: ClientList
                name: Client List 2023
                clients:
                  - id: 3
                    name: John
                  - id: 4
                    name: Jane
                """);

        var entityB = parseYaml("""
                kind: ClientList
                name: Client List 2022
                clients:
                  - id: 1
                    name: Bob
                  - id: 2
                    name: Alice
                    validationUrl: https://alice.example.com
                """);

        var view = parseView("""
                kind: /mica/view/v1
                name: test
                selector:
                  entityKind: ClientList
                data:
                  jsonPath: $.clients[*].['name', 'id', 'validationUrl']
                """);

        var result = renderer.render(view, Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals(2, result.data().size());

        var expected = """
                - - name: "John"
                    id: 3
                    validationUrl: null
                  - name: "Jane"
                    id: 4
                    validationUrl: null
                - - name: "Bob"
                    id: 1
                    validationUrl: null
                  - name: "Alice"
                    id: 2
                    validationUrl: "https://alice.example.com"
                """;
        assertEquals(expected, toYaml(result.data()));

        // now with flatten=true

        view = parseView("""
                kind: /mica/view/v1
                name: test
                selector:
                  entityKind: ClientList
                data:
                  jsonPath: $.clients[*].['name', 'id', 'validationUrl']
                  flatten: true
                """);

        result = renderer.render(view, Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals(4, result.data().size());

        expected = """
                - name: "John"
                  id: 3
                  validationUrl: null
                - name: "Jane"
                  id: 4
                  validationUrl: null
                - name: "Bob"
                  id: 1
                  validationUrl: null
                - name: "Alice"
                  id: 2
                  validationUrl: "https://alice.example.com"
                """;
        assertEquals(expected, toYaml(result.data()));
    }

    @Test
    public void useSimpleParameters() {
        var entityA = parseYaml("""
                kind: ClientList
                name: Client List 2023
                clients:
                  - id: 3
                    name: John
                  - id: 4
                    name: Jane
                """);

        var entityB = parseYaml("""
                kind: ClientList
                name: Client List 2022
                clients:
                  - id: 1
                    name: Bob
                  - id: 2
                    name: Alice
                    validationUrl: https://alice.example.com
                """);

        var view = parseView("""
                kind: /mica/view/v1
                name: test
                parameters:
                  properties:
                    clientId:
                        type: string
                selector:
                  entityKind: ClientList
                data:
                  jsonPath: $.clients[?(@.id==$parameters.clientId)].name
                  flatten: true
                """);

        var input = yamlMapper.convertValue(Map.of("clientId", (JsonNode) TextNode.valueOf("2")), JsonNode.class);
        var interpolatedView = interpolator.interpolate(view, input);
        var result = renderer.render(interpolatedView, Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals(1, result.data().size());

        var expected = """
                - "Alice"
                """;
        assertEquals(expected, toYaml(result.data()));
    }

    @Test
    public void mergeMultipleEntities() {
        var entityA = parseYaml("""
                kind: Item
                name: item-a
                foo:
                  bar: true
                baz: [0, 1, 2]
                qux:
                  ack: "hello!"
                """);

        var entityB = parseYaml("""
                kind: Item
                name: item-b
                foo:
                  bar: false
                baz: [3, 4, 5]
                qux:
                  eek: "bye!"
                """);

        var view = parseView("""
                kind: /mica/view/v1
                name: test
                selector:
                  entityKind: Item
                data:
                  jsonPath: $.['qux', 'foo', 'baz']
                  merge: true
                """);

        var result = renderer.render(view, Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals(1, result.data().size());

        var expected = """
                - qux:
                    ack: "hello!"
                    eek: "bye!"
                  foo:
                    bar: false
                  baz:
                  - 3
                  - 4
                  - 5
                """;
        assertEquals(expected, toYaml(result.data()));
    }

    @Test
    public void mergeOfEmptyValues() {
        var entityA = parseYaml("""
                kind: Item
                name: item-a
                someValue: 123
                stringValue: "foo"
                arrayValue: [0, 1, 2]
                anotherArrayValue: ['h', 'i', '!']
                objectValue:
                  foo: "bar"
                anotherObjectValue:
                  abc: "xyz"
                """);

        var entityB = parseYaml("""
                kind: Item
                name: item-b
                someValue: null
                stringValue: ""
                arrayValue: [3, 4, 5]
                anotherArrayValue: null
                objectValue:
                  baz: "qux"
                anotherObjectValue: null
                """);

        var view = parseView("""
                kind: /mica/view/v1
                name: test
                selector:
                  entityKind: Item
                data:
                  jsonPath: $.['someValue', 'stringValue', 'arrayValue', 'objectValue']
                  merge: true
                """);

        var result = renderer.render(view, Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals(1, result.data().size());

        var expected = """
                - someValue: null
                  stringValue: ""
                  arrayValue:
                  - 3
                  - 4
                  - 5
                  objectValue:
                    foo: "bar"
                    baz: "qux"
                """;
        assertEquals(expected, toYaml(result.data()));
    }

    @Test
    public void mergeByWorksAsIntended() {
        var entityA = parseYaml("""
                kind: /event/weather
                name: event-a
                city: "London"
                temperature: 20
                """);

        var entityB = parseYaml("""
                kind: /event/weather
                name: event-b
                city: "Paris"
                temperature: 21
                """);

        var entityC = parseYaml("""
                kind: /event/weather
                name: event-c
                city: "London"
                humidity: 50
                """);

        var entityD = parseYaml("""
                kind: /event/weather
                name: event-d
                city: "Paris"
                humidity: 51
                """);

        var view = parseView("""
                kind: /mica/view/v1
                name: /weather/report
                selector:
                  entityKind: /event/weather
                data:
                  jsonPath: $
                  mergeBy: $.city
                """);

        var result = renderer.render(view, Stream.of(entityA, entityB, entityC, entityD));
        assertNotNull(result);
        assertEquals(2, result.data().size());

        var expected = """
                - name: "event-c"
                  kind: "/event/weather"
                  city: "London"
                  temperature: 20
                  humidity: 50
                - name: "event-d"
                  kind: "/event/weather"
                  city: "Paris"
                  temperature: 21
                  humidity: 51
                """;
        assertEquals(expected, toYaml(result.data()));
    }

    @Test
    public void mergeByHandlesMissingKeys() {
        var entityA = parseYaml("""
                kind: /event/weather
                name: event-a
                city: "London"
                temperature: 20
                """);

        var entityB = parseYaml("""
                kind: /event/weather
                name: event-b
                city: "Paris"
                temperature: 21
                """);

        var entityC = parseYaml("""
                kind: /event/weather
                name: event-c
                humidity: 50
                """);

        var view = parseView("""
                kind: /mica/view/v1
                name: /weather/report
                selector:
                  entityKind: /event/weather
                data:
                  jsonPath: $
                  mergeBy: $.city
                """);

        var result = renderer.render(view, Stream.of(entityA, entityB, entityC));
        assertNotNull(result);
        assertEquals(3, result.data().size());

        var expected = """
                - name: "event-c"
                  kind: "/event/weather"
                  humidity: 50
                - name: "event-a"
                  kind: "/event/weather"
                  city: "London"
                  temperature: 20
                - name: "event-b"
                  kind: "/event/weather"
                  city: "Paris"
                  temperature: 21
                """;
        assertEquals(expected, toYaml(result.data()));
    }

    @Test
    public void applySimpleJsonPatch() {
        var entity = parseYaml("""
                kind: ListOfItems
                name: my-items
                widgets:
                  - id: 1
                    name: Plumbus
                  - id: 2
                    name: Fleeb
                  - id: 3
                    name: Schleem
                """);

        var view = parseView("""
                kind: /mica/view/v1
                name: test
                selector:
                  entityKind: ListOfItems
                data:
                  jsonPath: $
                  jsonPatch:
                    - op: add
                      path: /widgets/-
                      value:
                        id: 4
                        name: Blamf
                """);

        var result = renderer.render(view, Stream.of(entity));
        assertNotNull(result);
        assertEquals(1, result.data().size());
        assertEquals("Blamf", result.data().get(0).get("widgets").get(3).get("name").asText());
    }

    @Test
    public void jsonPatchFilterWorks() {
        var entityA = parseYaml("""
                kind: /mica/record/v1
                name: /entity-a
                data:
                  someValue: "hi!"
                  foo:
                    bar:
                      baz: "qux"
                      abc: "xyz"
                """);

        var entityB = parseYaml("""
                kind: /mica/record/v1
                name: /entity-b
                data:
                  someValue: "bye!"
                  foo:
                    bar:
                      baz: "qux"
                      abc: "xyz"
                """);

        var view = parseView("""
                kind: /mica/view/v1
                name: test
                selector:
                  entityKind: /mica/record/v1
                data:
                  jsonPath: $
                  jsonPatch:
                    - op: remove
                      path: /data/foo/bar/baz
                      ifMatches:
                        path: $.name
                        value: /entity-a
                """);

        var result = renderer.render(view, Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals(2, result.data().size());
        assertNull(result.data().get(0).get("data").get("foo").get("bar").get("baz"));
        assertEquals("xyz", result.data().get(0).get("data").get("foo").get("bar").get("abc").asText());
        assertEquals("qux", result.data().get(1).get("data").get("foo").get("bar").get("baz").asText());
    }

    @Test
    public void viewsMustReturnAllEntityFields() {
        var foo = parseYaml("""
                id: 88eccc0c-99e1-11ee-b9d1-0242ac120002
                kind: MyRecord
                name: foo
                status: active
                """);

        var bar = parseYaml("""
                id: 8d762a34-99e1-11ee-b9d1-0242ac120002
                kind: MyRecord
                name: bar
                status: inactive
                """);

        var view = parseView("""
                kind: /mica/view/v1
                name: test
                selector:
                  entityKind: MyRecord
                data:
                  jsonPath: $
                """);

        var result = renderer.render(view, Stream.of(foo, bar));
        assertEquals(2, result.data().size());
        assertEquals("88eccc0c-99e1-11ee-b9d1-0242ac120002", result.data().get(0).get("id").asText());
    }

    @Test
    public void entityNamesShouldBeReturned() {
        var foo = parseYaml("""
                kind: /myRecord
                name: /foo
                x: 1
                """);

        var bar = parseYaml("""
                kind: /myRecord
                name: /bar
                y: 2
                """);

        var view = parseView("""
                kind: /mica/view/v1
                name: test
                selector:
                  entityKind: /myRecord
                data:
                  jsonPath: $.z
                """);

        // the view that should return no data but entityNames should contain both
        // entities
        // (we fetched the entities but filtered them out by jsonPath)
        var result = renderer.render(view, Stream.of(foo, bar));
        assertEquals(0, result.data().size());
        assertEquals(2, result.entityNames().size());
        assertEquals("/foo", result.entityNames().get(0));
        assertEquals("/bar", result.entityNames().get(1));
    }

    @Test
    public void dropPropertiesWorkAsExpected() {
        var foo = parseYaml("""
                kind: /myRecord
                name: /foo
                x: 1
                """);

        var bar = parseYaml("""
                kind: /myRecord
                name: /bar
                y: 2
                """);

        var view = parseView("""
                kind: /mica/view/v1
                name: test
                selector:
                  entityKind: /myRecord
                data:
                  jsonPath: $
                  merge: true
                  dropProperties:
                    - name
                    - kind
                """);

        var result = renderer.render(view, Stream.of(foo, bar));
        assertEquals(1, result.data().size());
        assertEquals(2, result.data().get(0).size());
        assertEquals(1, result.data().get(0).get("x").asInt());
        assertEquals(2, result.data().get(0).get("y").asInt());
    }

    @Test
    public void jsonPathCanAccessStandardProperties() {
        var fooId = UUID.randomUUID().toString();
        var foo = parseYaml("""
                id: %s
                kind: /testKind
                name: /foo
                """.formatted(fooId));

        var barId = UUID.randomUUID().toString();
        var bar = parseYaml("""
                id: %s
                kind: /testKind
                name: /bar
                """.formatted(barId));

        var view = parseView("""
                kind: /mica/view/v1
                name: test
                selector:
                  entityKind: /testKind
                data:
                  jsonPath: $.['id', 'name']
                """);

        var result = renderer.render(view, Stream.of(foo, bar));
        var ids = result.data().stream().map(n -> n.get("id").asText()).toList();
        assertTrue(ids.contains(fooId));
        assertTrue(ids.contains(barId));
    }

    @Test
    public void lotsOfEntities() {
        var foos = IntStream.iterate(0, i -> i < 500, i -> i + 1)
                .mapToObj(i -> parseYaml("""
                        kind: /myRecord
                        name: /foo-%d
                        x: 1
                        """.formatted(i)));

        var bars = IntStream.iterate(0, i -> i < 500, i -> i + 1)
                .mapToObj(i -> parseYaml("""
                        kind: /myRecord
                        name: /bar-%d
                        y: 2
                        """.formatted(i)));

        var entities = new ArrayList<>(Stream.concat(foos, bars).toList());
        Collections.shuffle(entities);

        var view = parseView("""
                kind: /mica/view/v1
                name: test
                selector:
                  entityKind: /myRecord
                  namePatterns:
                    - /foo-.*
                data:
                  jsonPath: $.x
                """);
        var result = renderer.render(view, entities.stream());
        assertEquals(500, result.data().size());
    }

    @Test
    public void unknownNestedPropertiesAreAllowed() {
        var fooId = UUID.randomUUID().toString();
        var foo = parseYaml("""
                id: %s
                kind: /nestedPropertyTest
                name: /foo
                data:
                  foo: 456
                """.formatted(fooId));

        var barId = UUID.randomUUID().toString();
        var bar = parseYaml("""
                id: %s
                kind: /nestedPropertyTest
                name: /bar
                data:
                  bar: 678
                """.formatted(barId));

        var view = parseView("""
                kind: /mica/view/v1
                name: /test
                selector:
                  entityKind: /nestedPropertyTest
                data:
                  jsonPath: $.data.foo # only exists in one of the entities
                """);

        var result = renderer.render(view, Stream.of(foo, bar));
        assertEquals(1, result.data().size());
        assertEquals(456, result.data().get(0).asInt());
        assertEquals(2, result.entityNames().size());
        assertTrue(result.entityNames().containsAll(Set.of("/foo", "/bar")));
    }

    @Test
    public void multipleJsonPathsCanBeUsed() {
        var foo = parseYaml("""
                kind: /test
                name: /foo
                a: 0
                x: 1
                y:
                  z: 2
                """);

        var bar = parseYaml("""
                kind: /test
                name: /bar
                a: 0
                x: 3
                """);

        var view = parseView("""
                kind: /mica/view/v1
                name: test
                selector:
                  entityKind: /test
                data:
                  jsonPath:
                    - $.['x', 'y']
                    - $.x
                """);

        var result = renderer.render(view, Stream.of(foo, bar));
        assertEquals(2, result.data().size());
        assertEquals(1, result.data().get(0).asInt());
        assertEquals(3, result.data().get(1).asInt());
    }

    @Test
    public void mapWorksAsIntended() {
        var entityA = parseYaml("""
                kind: /test
                name: /a
                foo:
                  value: 123
                  nested:
                    bar: 345
                """);

        var entityB = parseYaml("""
                kind: /test
                name: /b
                foo:
                  value: 678
                """);

        var view = parseView("""
                kind: /mica/view/v1
                name: /test
                selector:
                  entityKind: /test
                data:
                  jsonPath: $
                  map:
                    x:
                      - $.foo
                      - $.value
                    y: $.foo.nested.bar
                """);

        var result = renderer.render(view, Stream.of(entityA, entityB));
        assertEquals(2, result.data().size());
        assertEquals(123, result.data().get(0).get("x").asInt());
        assertEquals(345, result.data().get(0).get("y").asInt());
        assertEquals(678, result.data().get(1).get("x").asInt());
    }

    private static ViewLike parseView(@Language("yaml") String yaml) {
        return asViewLike(objectMapper, parseYaml(yaml));
    }

    private static PartialEntity parseYaml(@Language("yaml") String yaml) {
        try {
            return yamlMapper.readValue(yaml, PartialEntity.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String toYaml(Object o) {
        try {
            return yamlMapper.prettyPrint(o);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
