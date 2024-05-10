package ca.ibodrov.mica.server.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PropertiesTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void formatAsPropertiesWorksAsExpected() throws IOException {
        var node = objectMapper.readTree("""
                {
                    "a": 1,
                    "b": "2",
                    "c": {
                        "d": 3
                    },
                    "e.f": "hello",
                    "g": {
                        "h": "world"
                    }
                }
                """);
        var result = ViewController.formatAsProperties((ObjectNode) node);
        assertEquals("1", result.get("a"));
        assertEquals("2", result.get("b"));
        assertEquals("3", result.get("c.d"));
        assertEquals("hello", result.get("e.f"));
        assertEquals("world", result.get("g.h"));
    }
}
