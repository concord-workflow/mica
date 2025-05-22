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
