package ca.ibodrov.mica.server.data.meta;

import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class MetadataScannerTest {

    @Test
    public void parseSimpleExample() {
        var result = MetadataScanner.parseConcordYaml(Stream.of(
                "# regular comment",
                "configuration:",
                "  arguments: null",
                "flows:",
                "     # regular comment with weird indentation",
                "        ",
                "  ##",
                "  #  in:",
                "  #    foo: string, mandatory, path to foo to discombobulate",
                "  #    bar: string, optional, other stuff",
                "  #  out:",
                "  #    baz: string, optional, stuff in out",
                "  ##",
                "  default:",
                "    - log: 'hello'",
                "",
                "",
                "  ##",
                "  #  in:",
                "  #    qux: string, mandatory, path to foo to discombobulate",
                "  #  out:",
                "  #    waldo: string, optional, stuff in out",
                "  ##",
                "  other:",
                "    - log: 'world'",
                ""));

        assertEquals(0, result.warnings().size());

        assertEquals("default", result.items().get(0).name());
        assertEquals("foo", result.items().get(0).inParameters().get(0).name());
        assertEquals("string", result.items().get(0).inParameters().get(0).type());
        assertTrue(result.items().get(0).inParameters().get(0).required());
        assertEquals("path to foo to discombobulate", result.items().get(0).inParameters().get(0).description());

        assertEquals("other", result.items().get(1).name());
        assertEquals("qux", result.items().get(1).inParameters().get(0).name());
        assertEquals("string", result.items().get(1).inParameters().get(0).type());
        assertTrue(result.items().get(1).inParameters().get(0).required());
        assertEquals("path to foo to discombobulate", result.items().get(1).inParameters().get(0).description());

        assertEquals("waldo", result.items().get(1).outParameters().get(0).name());
        assertEquals("string", result.items().get(1).outParameters().get(0).type());
        assertFalse(result.items().get(1).outParameters().get(0).required());
        assertEquals("stuff in out", result.items().get(1).outParameters().get(0).description());
    }

    @Test
    public void parseEmpty() {
        var result = MetadataScanner.parseConcordYaml("""
                ## foobar
                flows:
                  default:
                    - log: "Hello, ${name}"
                """.lines());

        assertEquals(0, result.warnings().size());
        assertEquals(1, result.items().size());
        assertEquals("default", result.items().get(0).name());
    }

    @Test
    public void parseSlightlyWeirdIndentation() {
        var result = MetadataScanner.parseConcordYaml("""
                flows:
                 default:
                       - log: "Hello, ${name}"
                """.lines());

        assertEquals(0, result.warnings().size());
        assertEquals(1, result.items().size());
        assertEquals("default", result.items().get(0).name());

        result = MetadataScanner.parseConcordYaml("""
                flows:
                 ##
                 #  in:
                 #    name: string, mandatory, the name
                 ##
                 default:
                       - log: "Hello, ${name}"
                """.lines());

        assertEquals(0, result.warnings().size());
        assertEquals(1, result.items().size());
        assertEquals("default", result.items().get(0).name());
        assertEquals("name", result.items().get(0).inParameters().get(0).name());
        assertEquals("the name", result.items().get(0).inParameters().get(0).description());

        result = MetadataScanner.parseConcordYaml("""
                flows:
                 ##
                 #  in:
                 #     name: string, mandatory, the name
                 #   age: int, optional, the age
                 ##
                 default:
                       - log: "Hello, ${name}"
                """.lines());

        assertEquals(0, result.warnings().size());
        assertEquals(1, result.items().size());
        assertEquals("default", result.items().get(0).name());
        assertEquals("name", result.items().get(0).inParameters().get(0).name());
        assertEquals("the name", result.items().get(0).inParameters().get(0).description());
        assertEquals("age", result.items().get(0).inParameters().get(1).name());
        assertEquals("the age", result.items().get(0).inParameters().get(1).description());
    }

    @Test
    public void parseGarbage() {
        var result = MetadataScanner.parseConcordYaml("""
                lorem ipsum dolor
                sit amet
                """.lines());

        assertEquals("No flows found", result.warnings().get(0).message());
        assertEquals(0, result.items().size());

        result = MetadataScanner.parseConcordYaml("""
                flows:
                  - log 'hellooo...?
                """.lines());

        assertEquals("No flows found", result.warnings().get(0).message());
        assertEquals(0, result.items().size());
    }
}
