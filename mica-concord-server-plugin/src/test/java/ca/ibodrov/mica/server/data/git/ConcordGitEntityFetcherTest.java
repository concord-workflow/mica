package ca.ibodrov.mica.server.data.git;

import ca.ibodrov.mica.server.YamlMapper;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;

import static ca.ibodrov.mica.server.data.git.ConcordGitEntityFetcher.DEFAULT_FILE_FORMAT_OPTIONS;
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
                .walkAndParse(yamlMapper, tempDir, ".*", true, "", EnumSet.allOf(FileFormat.class),
                        DEFAULT_FILE_FORMAT_OPTIONS)
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

    @Test
    public void testCustomFileNamePatters(@TempDir Path tempDir) throws Exception {
        var fooProperties = tempDir.resolve("foo.my-props");
        Files.writeString(fooProperties, """
                a=one
                b=two
                """);

        var barProperties = tempDir.resolve("bar.properties");
        Files.writeString(barProperties, """
                a=1
                b=2
                """);

        var options = Map.of(FileFormat.PROPERTIES, new FileFormatOptions(".*\\.my-props"));
        var result = ConcordGitEntityFetcher
                .walkAndParse(yamlMapper, tempDir, ".*", true, "", EnumSet.allOf(FileFormat.class), options)
                .toList();

        assertEquals(1, result.size());
        assertEquals("foo", result.get(0).name());
    }
}
