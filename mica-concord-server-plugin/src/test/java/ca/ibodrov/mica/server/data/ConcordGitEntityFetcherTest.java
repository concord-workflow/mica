package ca.ibodrov.mica.server.data;

import ca.ibodrov.mica.server.YamlMapper;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConcordGitEntityFetcherTest {

    private static final YamlMapper yamlMapper = new YamlMapper(new ObjectMapperProvider().get());

    @Test
    public void testWalkAndParse(@TempDir Path tempDir) throws Exception {
        var fooYaml = tempDir.resolve("foo.yaml");
        Files.writeString(fooYaml, """
                kind: /test/kind/v1
                a: 1
                b: 2
                c:
                  d: 3
                """);

        var barProperties = tempDir.resolve("bar.properties");
        Files.writeString(barProperties, """
                a=1
                c.d=three
                e=false
                f={"hello": "world"}
                """);

        var randomStuff = tempDir.resolve("random-stuff.txt");
        Files.writeString(randomStuff, "hello");

        var result = ConcordGitEntityFetcher
                .walkAndParse(yamlMapper, tempDir, ".*", true, "", EnumSet.allOf(FileFormat.class))
                .toList();

        assertEquals(2, result.size());

        var entityFoo = result.stream().filter(e -> e.name().equals("foo")).findFirst().orElseThrow();
        assertEquals(1, entityFoo.data().get("a").asInt());
        assertEquals(3, entityFoo.data().get("c").get("d").asInt());

        var entityBar = result.stream().filter(e -> e.name().equals("bar")).findFirst().orElseThrow();
        assertEquals("1", entityBar.data().get("a").asText());
        assertEquals("three", entityBar.data().get("c.d").asText());
        assertEquals("{\"hello\": \"world\"}", entityBar.data().get("f").asText());
    }
}
