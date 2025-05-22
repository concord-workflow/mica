package ca.ibodrov.mica.server.ui;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static ca.ibodrov.mica.server.ui.EditorSchemaResource.findReplace;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EditorSchemaResourceTest {

    private static final ObjectMapper objectMapper = new ObjectMapperProvider().get();

    @Test
    public void validateFindReplace() {
        var input = parseYaml("""
                allOf: [{$ref: "mica://internal/mica/standard-properties/v1"}]
                properties:
                  kind:
                    $ref: "mica://internal/mica/standard-properties/v1/#/properties/kind"
                deep:
                  deeper: [
                    { $ref: "mica://internal/mica/standard-properties/v1" }
                  ]
                """);

        var output = findReplace(input, "$ref",
                s -> s.replace("mica://internal/mica/standard-properties/v1", "replaced"));
        assertEquals("replaced", output.get("allOf").get(0).get("$ref").asText());
        assertEquals("replaced/#/properties/kind", output.get("properties").get("kind").get("$ref").asText());
        assertEquals("replaced", output.get("deep").get("deeper").get(0).get("$ref").asText());
    }

    private static ObjectNode parseYaml(@Language("yaml") String s) {
        try {
            return objectMapper.copyWith(new YAMLFactory()).readValue(s, ObjectNode.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
