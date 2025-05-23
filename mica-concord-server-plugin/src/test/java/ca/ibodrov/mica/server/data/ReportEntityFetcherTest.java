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

import ca.ibodrov.mica.api.model.PartialEntity;
import ca.ibodrov.mica.server.TestSchemas;
import ca.ibodrov.mica.server.YamlMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.walmartlabs.concord.common.ObjectMapperProvider;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static ca.ibodrov.mica.server.data.BuiltinSchemas.STANDARD_PROPERTIES_REF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReportEntityFetcherTest {

    private static final ObjectMapper objectMapper = new ObjectMapperProvider().get();
    private static final YamlMapper yamlMapper = new YamlMapper(objectMapper);

    @Test
    public void validateNoUnmappedPropertiesReport() {
        var testRecordKind = parseYaml("""
                name: /test/record
                kind: /mica/kind/v1
                schema:
                  $ref: %s
                  properties:
                    x:
                      type: number
                    y:
                      type: number
                """.formatted(STANDARD_PROPERTIES_REF));

        var fooDoc = parseYaml("""
                name: /test/foo
                kind: /test/record
                x: 1
                y: 2
                z: 3
                """);

        var validator = Validator.getDefault(objectMapper, TestSchemas.getBuiltinSchemaFetcher(yamlMapper));
        var schemaFactory = validator.getJsonSchemaFactory();
        var schemaJson = testRecordKind.data().get("schema");
        assertTrue(schemaJson.isObject());
        ((ObjectNode) schemaJson).put("unevaluatedProperties", false);
        var schema = schemaFactory.getSchema(schemaJson);
        var input = objectMapper.convertValue(fooDoc, JsonNode.class);
        var result = schema.validate(input).stream().toList();
        assertEquals(1, result.size());
        assertEquals("unevaluatedProperties", result.get(0).getType());
        // TODO turn into a component
    }

    private static PartialEntity parseYaml(@Language("yaml") String yaml) {
        try {
            return yamlMapper.readValue(yaml, PartialEntity.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
