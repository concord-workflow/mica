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
                kind: MicaView/v1
                name: %s
                selector:
                  entityKind: %s
                data:
                  jsonPath: %s
                  flatten: %s
                """.formatted("test", "MicaRecord/v1", "$.data", false));

        var entityA = parseYaml("""
                kind: MicaRecord/v1
                name: A
                data: Some data from A
                """);

        var entityB = parseYaml("""
                kind: MicaRecord/v1
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
                kind: MicaView/v1
                name: test
                selector:
                  entityKind: ClientList
                data:
                  jsonPath: $.clients[*].['name', 'id', 'validationUrl']
                """);

        var result = processor.render(view, Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals(1, result.data().size());

        var expected = """
                ---
                data:
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
                kind: MicaView/v1
                name: test
                selector:
                  entityKind: ClientList
                data:
                  jsonPath: $.clients[*].['name', 'id', 'validationUrl']
                  flatten: true
                """);

        result = processor.render(view, Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals(1, result.data().size());

        expected = """
                ---
                data:
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
                kind: MicaView/v1
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
        assertEquals(1, result.data().size());

        var expected = """
                ---
                data:
                - "Bob"
                """;
        assertEquals(expected, toYaml(result.data()));
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
