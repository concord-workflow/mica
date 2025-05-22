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

import ca.ibodrov.mica.server.data.Validator.NoopSchemaFetcher;
import ca.ibodrov.mica.server.data.Validator.SchemaFetcher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ValidatorTest {

    @Test
    public void testSimple() {
        var objectMapper = new ObjectMapperProvider().get();
        var validator = Validator.getDefault(objectMapper, new TestSchemaFetcher());
        var schema = parseYaml(objectMapper, """
                $ref: 'mica://internal/bar'
                properties:
                  foo:
                    type: string
                required: [foo]
                """);
        var input = parseYaml(objectMapper, "{}");
        var result = validator.validateObject(schema, input);
        assertEquals(2, result.messages().size());
    }

    @Test
    public void testInvalidType() {
        var objectMapper = new ObjectMapperProvider().get();
        var validator = Validator.getDefault(objectMapper, new NoopSchemaFetcher());
        var schema = parseYaml(objectMapper, """
                type: object
                properties:
                  foo:
                    type: string
                required: [foo]
                """);
        var input = parseYaml(objectMapper, "'hello!'");
        var result = validator.validateObject(schema, input);
        assertEquals(1, result.messages().size());
    }

    private static class TestSchemaFetcher implements SchemaFetcher {

        @Override
        public Optional<InputStream> fetch(String kind) {
            return Optional.of(new ByteArrayInputStream("""
                    {
                        "type": "object",
                        "properties": {
                            "bar": {
                                "type": "string"
                            }
                        },
                        "required": [
                            "bar"
                        ]
                    }
                    """.getBytes()));
        }
    }

    private static JsonNode parseYaml(ObjectMapper mapper, @Language("yaml") String s) {
        try {
            return mapper.copyWith(new YAMLFactory()).readValue(s, JsonNode.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
