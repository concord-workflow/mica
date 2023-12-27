package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.api.model.ViewLike;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

import static ca.ibodrov.mica.server.data.BuiltinSchemas.asViewLike;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ViewRendererTest {

    private static final ObjectMapper yamlMapper = new ObjectMapperProvider().get()
            .copyWith(new YAMLFactory());

    private static final ViewRenderer renderer = new ViewRenderer(yamlMapper);

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

        var result = renderer.render(view, NullNode.getInstance(), Stream.of(entityA, entityB));
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

        var result = renderer.render(view, NullNode.getInstance(), Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals(2, result.data().size());

        var expected = """
                ---
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

        result = renderer.render(view, NullNode.getInstance(), Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals(4, result.data().size());

        expected = """
                ---
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

        var result = renderer.render(view, parameters("clientId", IntNode.valueOf(1)), Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals(1, result.data().size());

        var expected = """
                ---
                - "Bob"
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

        var result = renderer.render(view, NullNode.getInstance(), Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals(1, result.data().size());

        var expected = """
                ---
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

        var result = renderer.render(view, NullNode.getInstance(), Stream.of(entity));
        assertNotNull(result);
        assertEquals(1, result.data().size());
        assertEquals("Blamf", result.data().get(0).get("widgets").get(3).get("name").asText());
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

        var result = renderer.render(view, NullNode.getInstance(), Stream.of(foo, bar));
        assertEquals(2, result.data().size());
        assertEquals("88eccc0c-99e1-11ee-b9d1-0242ac120002", result.data().get(0).get("id").asText());
    }

    private static ViewLike parseView(@Language("yaml") String yaml) {
        return asViewLike(yamlMapper, parseYaml(yaml));
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
            return yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static JsonNode parameters(String k1, JsonNode v1) {
        return yamlMapper.convertValue(Map.of(k1, v1), JsonNode.class);
    }
}
