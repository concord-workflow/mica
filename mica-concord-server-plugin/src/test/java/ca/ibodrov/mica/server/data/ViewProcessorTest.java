package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.api.model.PartialEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ViewProcessorTest {

    private static final ObjectMapper yamlMapper = new ObjectMapperProvider().get()
            .copyWith(new YAMLFactory());

    private static final ViewProcessor processor = new ViewProcessor(yamlMapper);

    @Test
    public void testSimpleRender() {
        var view = view("test", "MicaRecord/v1", "$.data");

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

        var result = processor.process(view, Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals("Some data from A", result.data().get("data").get(0).asText());
        assertEquals("Some data from B", result.data().get("data").get(1).asText());
    }

    @Test
    public void testComplex() {
        // TODO a bit more complicated json path
        var view = view("test", "ClientList", "$.clients[*].name");

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
                """);

        var result = processor.process(view, Stream.of(entityA, entityB));
        assertNotNull(result);
        assertEquals("John", result.data().get("data").get(0).get(0).asText());
        assertEquals("Jane", result.data().get("data").get(0).get(1).asText());
        assertEquals("Bob", result.data().get("data").get(1).get(0).asText());
        assertEquals("Alice", result.data().get("data").get(1).get(1).asText());
    }

    private static PartialEntity parseYaml(@Language("yaml") String yaml) {
        try {
            return yamlMapper.readValue(yaml, PartialEntity.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static PartialEntity view(String viewName, String selectorEntityKind, String dataJsonPath) {
        return parseYaml("""
                kind: MicaView/v1
                name: %s
                selector:
                  entityKind: %s
                data:
                  jsonPath: %s
                """.formatted(viewName, selectorEntityKind, dataJsonPath));
    }
}
