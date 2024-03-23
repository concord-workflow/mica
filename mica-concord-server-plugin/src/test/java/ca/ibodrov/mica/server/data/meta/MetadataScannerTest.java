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
                "     # regular comment with a weird indentation",
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

        assertEquals("foo", result.items().get(0).inParameters().get(0).name());
        assertEquals("string", result.items().get(0).inParameters().get(0).type());
        assertTrue(result.items().get(0).inParameters().get(0).required());
        assertEquals("path to foo to discombobulate", result.items().get(0).inParameters().get(0).description());

        assertEquals("bar", result.items().get(0).inParameters().get(1).name());
        assertEquals("string", result.items().get(0).inParameters().get(1).type());
        assertFalse(result.items().get(0).inParameters().get(1).required());
        assertEquals("other stuff", result.items().get(0).inParameters().get(1).description());

        assertEquals("baz", result.items().get(0).outParameters().get(0).name());
        assertEquals("string", result.items().get(0).outParameters().get(0).type());
        assertFalse(result.items().get(0).outParameters().get(0).required());
        assertEquals("stuff in out", result.items().get(0).outParameters().get(0).description());

        assertEquals("qux", result.items().get(1).inParameters().get(0).name());
        assertEquals("string", result.items().get(1).inParameters().get(0).type());
        assertTrue(result.items().get(1).inParameters().get(0).required());
        assertEquals("path to foo to discombobulate", result.items().get(1).inParameters().get(0).description());

        assertEquals("waldo", result.items().get(1).outParameters().get(0).name());
        assertEquals("string", result.items().get(1).outParameters().get(0).type());
        assertFalse(result.items().get(1).outParameters().get(0).required());
        assertEquals("stuff in out", result.items().get(1).outParameters().get(0).description());
    }
}
