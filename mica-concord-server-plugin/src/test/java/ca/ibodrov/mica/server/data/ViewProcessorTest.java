package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.api.model.ViewLike;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

import static ca.ibodrov.mica.server.data.BuiltinSchemas.asView;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ViewProcessorTest {

    private static final ObjectMapper yamlMapper = new ObjectMapperProvider().get()
            .copyWith(new YAMLFactory());

    private static final ViewProcessor processor = new ViewProcessor(yamlMapper);

    @Test
    public void testSimpleRender() {
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

        var result = processor.render(view, Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals("Some data from A", result.data().get("data").get(0).asText());
        assertEquals("Some data from B", result.data().get("data").get(1).asText());
    }

    @Test
    public void testComplex() {
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

        var result = processor.render(view, Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals(2, result.data().get("data").size());

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
        assertEquals(expected, toYaml(result.data().get("data")));

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

        result = processor.render(view, Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals(4, result.data().get("data").size());

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
        assertEquals(expected, toYaml(result.data().get("data")));
    }

    @Test
    public void testSimpleParameters() {
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
                  clientId:
                    type: string
                selector:
                  entityKind: ClientList
                data:
                  jsonPath: $.clients[?(@.id==$clientId)].name
                  flatten: true
                """);

        var result = processor.render(view, Map.of("clientId", IntNode.valueOf(1)), Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals(1, result.data().get("data").size());

        var expected = """
                ---
                - "Bob"
                """;
        assertEquals(expected, toYaml(result.data().get("data")));
    }

    @Test
    public void testMerge() {
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
                  jsonPath: $
                  merge: true
                """);

        var result = processor.render(view, Map.of(), Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals(1, result.data().get("data").size());

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
        assertEquals(expected, toYaml(result.data().get("data")));
    }

    @Test
    public void testJsonPatch() {
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

        var result = processor.render(view, Map.of(), Stream.of(entity));
        assertNotNull(result);
        assertEquals(1, result.data().get("data").size());
        assertEquals("Blamf", result.data().get("data").get(0).get("widgets").get(3).get("name").asText());
    }

    private static ViewLike parseView(@Language("yaml") String yaml) {
        return asView(yamlMapper, parseYaml(yaml));
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

}
