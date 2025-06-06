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

import ca.ibodrov.mica.server.exceptions.ApiException;
import ca.ibodrov.mica.server.exceptions.ViewProcessorException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;

import javax.inject.Inject;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class JsonPathEvaluator {

    private final ObjectMapper objectMapper;
    private final ParseContext parseContext;

    @Inject
    public JsonPathEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = requireNonNull(objectMapper);
        this.parseContext = JsonPath.using(Configuration.builder()
                .options(Option.DEFAULT_PATH_LEAF_TO_NULL)
                // a custom JsonProvider that supports both JsonNode and Map
                .jsonProvider(new MicaJsonProvider(objectMapper))
                .build());
    }

    public Optional<JsonNode> applyInApiCall(JsonNode data,
                                             String jsonPath) {
        try {
            return apply(data, jsonPath);
        } catch (JsonPathException e) {
            throw ApiException
                    .badRequest("Error while processing JSON path: " + e.getMessage());
        }
    }

    public Optional<JsonNode> apply(JsonNode data, String jsonPath) {
        Object result;
        try {
            result = parseContext.parse(data).read(jsonPath);
        } catch (PathNotFoundException e) {
            return Optional.empty();
        } catch (IllegalArgumentException | JsonPathException e) {
            throw new JsonPathException("%s (%s)".formatted(e.getMessage(), jsonPath));
        }
        if (result == null || result instanceof NullNode) {
            return Optional.empty();
        }
        if (!(result instanceof JsonNode)) {
            try {
                result = objectMapper.convertValue(result, JsonNode.class);
            } catch (IllegalArgumentException e) {
                throw new ViewProcessorException("Expected a JsonNode, got: " + result.getClass());
            }
        }
        return Optional.of((JsonNode) result);
    }

    private static class MicaJsonProvider extends JacksonJsonNodeJsonProvider {

        public MicaJsonProvider(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        public boolean isMap(Object obj) {
            return super.isMap(obj) || obj instanceof Map;
        }

        @Override
        public Object getMapValue(Object obj, String key) {
            if (obj instanceof ObjectNode) {
                return super.getMapValue(obj, key);
            }
            if (obj instanceof Map) {
                return ((Map<?, ?>) obj).get(key);
            }
            throw new ViewProcessorException("Expected a Map, got: " + obj.getClass());
        }

        @Override
        public String toString() {
            return "mica-json-provider";
        }
    }
}
