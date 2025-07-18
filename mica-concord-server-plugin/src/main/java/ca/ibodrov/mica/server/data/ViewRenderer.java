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

import ca.ibodrov.mica.api.model.EntityLike;
import ca.ibodrov.mica.api.model.ViewLike;
import ca.ibodrov.mica.server.data.js.JsEvaluator;
import ca.ibodrov.mica.server.exceptions.ViewProcessorException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.zjsonpatch.InvalidJsonPatchException;
import com.flipkart.zjsonpatch.JsonPatch;
import com.flipkart.zjsonpatch.JsonPatchApplicationException;
import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.JsonPathException;

import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.StreamSupport.stream;

public class ViewRenderer {

    private final JsonPathEvaluator jsonPathEvaluator;
    private final JsEvaluator jsEvaluator;
    private final ObjectMapper objectMapper;
    private final JsonMapper mergeMapper;

    public ViewRenderer(JsonPathEvaluator jsonPathEvaluator, JsEvaluator jsEvaluator, ObjectMapper objectMapper) {
        this.jsonPathEvaluator = requireNonNull(jsonPathEvaluator);
        this.objectMapper = requireNonNull(objectMapper);
        this.jsEvaluator = requireNonNull(jsEvaluator);
        this.mergeMapper = JsonMapper.builder()
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .withConfigOverride(ArrayNode.class, cfg -> cfg.setMergeable(false))
                .build();
    }

    public RenderedView render(ViewLike view, Stream<? extends EntityLike> entities) {
        return render(view, RenderOverrides.none(), entities);
    }

    /**
     * Render a /mica/view/v1 using the given entities and parameters.
     */
    public RenderedView render(ViewLike view, RenderOverrides overrides, Stream<? extends EntityLike> entities) {
        // interpolate JSON path using the supplied parameters
        var jsonPath = requireNonNull(view.data().jsonPath());

        var entityNames = ImmutableList.<String>builder();

        // apply JSON path
        var data = entities
                // ...while we are at it, collect the entity names
                .peek(entity -> entityNames.add(entity.name()))
                .map(row -> applyAllJsonPaths(objectMapper.convertValue(row, JsonNode.class), jsonPath))
                .flatMap(Optional::stream)
                .toList();

        if (data.isEmpty()) {
            return RenderedView.empty(view, entityNames.build());
        }

        // flatten - convert an array of arrays by concatenating them into a single
        // array
        var flatten = view.data().flatten().orElse(false);
        if (flatten && data.stream().allMatch(JsonNode::isArray)) {
            data = data.stream()
                    .flatMap(node -> stream(spliteratorUnknownSize(node.elements(), Spliterator.ORDERED), false))
                    .toList();
        }

        // mergeBy - group by a JSON path and merge the groups
        var mergeBy = view.data().mergeBy().filter(JsonNode::isTextual)
                .map(JsonNode::asText);

        if (mergeBy.isPresent()) {
            data = data.stream()
                    .collect(groupingBy(
                            node -> jsonPathEvaluator
                                    .applyInApiCall(node, mergeBy.get())
                                    .orElse(NullNode.getInstance())))
                    .entrySet().stream()
                    .flatMap(entry -> {
                        var rows = entry.getValue();
                        return rows.stream()
                                .reduce((a, b) -> deepMerge((ObjectNode) a, (ObjectNode) b))
                                .stream();
                    })
                    .toList();
        } else {
            // merge - convert an array of objects into a single object
            var merge = overrides.alwaysMerge() || view.data().merge().orElse(false);
            if (merge && data.stream().allMatch(JsonNode::isObject)) {
                var mergedData = data.stream()
                        .reduce((a, b) -> deepMerge((ObjectNode) a, (ObjectNode) b))
                        .orElseThrow(() -> new ViewProcessorException("Expected a merge result, got nothing"));
                data = List.of(mergedData);
            }
        }

        // apply JSON patch
        var patch = view.data().jsonPatch().filter(p -> !p.isNull());
        if (patch.isPresent()) {
            var patchData = patch.get();

            try {
                JsonPatch.validate(patchData);
            } catch (InvalidJsonPatchException e) {
                throw new ViewProcessorException("Invalid data.jsonPatch: " + e.getMessage());
            }

            data = data.stream()
                    .map(node -> applyJsonPatch(node, patchData))
                    .toList();
        }

        // drop properties if requested
        var dropProperties = view.data().dropProperties().orElse(List.of());
        if (!dropProperties.isEmpty()) {
            data.forEach(node -> {
                if (!node.isObject()) {
                    throw new ViewProcessorException(
                            "dropProperties can only be applied to arrays of objects. The data is an array of %ss"
                                    .formatted(node.getNodeType()));
                }
                ((ObjectNode) node).remove(dropProperties);
            });
        }

        // apply "map"
        var map = view.data().map();
        if (map.isPresent()) {
            data = data.stream()
                    .map(node -> {
                        if (!node.isObject()) {
                            throw new ViewProcessorException(
                                    "map can only be applied to arrays of objects. The data is an array of %ss"
                                            .formatted(node.getNodeType()));
                        }
                        var result = objectMapper.createObjectNode();
                        map.get().forEach((key, value) -> {
                            var output = applyAllJsonPaths(node, value);
                            output.ifPresent(jsonNode -> result.set(key, jsonNode));
                        });
                        return (JsonNode) result;
                    })
                    .toList();
        }

        // apply "template"
        var template = view.data().template().filter(t -> !t.isNull());
        if (template.isPresent()) {
            data = data.stream()
                    .map(node -> applyTemplate(node, template.get()))
                    .toList();
        }

        // apply "js"
        var js = view.data().js().filter(v -> !v.isBlank());
        if (js.isPresent()) {
            data = jsEvaluator.eval(js.get(), data);
        }

        return new RenderedView(view, data, entityNames.build());
    }

    private Optional<JsonNode> applyAllJsonPaths(JsonNode data, JsonNode jsonPath) {
        if (jsonPath.isTextual()) {
            return jsonPathEvaluator.applyInApiCall(data, jsonPath.asText());
        } else if (jsonPath.isArray()) {
            var result = data;
            for (int i = 0; i < jsonPath.size(); i++) {
                var node = jsonPath.get(i);
                String jsonPath1 = node.asText();
                var output = jsonPathEvaluator.applyInApiCall(result, jsonPath1);
                if (output.isEmpty()) {
                    return Optional.empty();
                }
                result = output.get();
            }
            return Optional.of(result);
        } else {
            throw new ViewProcessorException(
                    "Expected a string or an array of strings as JSON path, got a " + jsonPath.getNodeType());
        }
    }

    private JsonNode applyJsonPatch(JsonNode node, JsonNode patchData) {
        if (!patchData.isArray()) {
            throw new ViewProcessorException(
                    "jsonPatch must be a list of JSON patch operations. Got %s".formatted(patchData.getNodeType()));
        }

        // filter out operations that do not apply to the current node
        var effectivePatchData = objectMapper.createArrayNode();
        patchData.forEach(op -> {
            var ifMatches = op.get("ifMatches");

            if (ifMatches != null) {
                var matchPath = ifMatches.get("path");
                if (matchPath == null) {
                    throw new ViewProcessorException("JSON path is required in ifMatches.path");
                }

                var matchValue = ifMatches.get("value");
                if (matchValue == null) {
                    throw new ViewProcessorException("Value is required in ifMatches.value");
                }

                try {
                    var actualValue = jsonPathEvaluator.apply(node, matchPath.asText());
                    actualValue.ifPresent(v -> {
                        if (matchValue.equals(v)) {
                            effectivePatchData.add(op);
                        }
                    });
                } catch (JsonPathException e) {
                    throw new ViewProcessorException(
                            "Error while applying a JSON patch operation %s: %s".formatted(op, e.getMessage()));
                }
            } else {
                effectivePatchData.add(op);
            }
        });

        if (!node.isContainerNode()) {
            throw new ViewProcessorException(
                    "JSON patch can only be applied to arrays of objects and array of arrays. The data is an array of %ss"
                            .formatted(node.getNodeType()));
        }

        try {
            return JsonPatch.apply(effectivePatchData, node);
        } catch (JsonPatchApplicationException e) {
            throw new ViewProcessorException(
                    "Error while applying data.jsonPatch: " + e.getMessage());
        }
    }

    private JsonNode applyTemplate(JsonNode node, JsonNode template) {
        if (template.isNull()) {
            return template;
        } else if (template.isTextual()) {
            var text = template.asText();
            if (text.startsWith("$")) {
                return applyAllJsonPaths(node, template).orElse(NullNode.getInstance());
            }
        } else if (template.isArray()) {
            if (template.isEmpty()) {
                return template;
            }

            for (var i = 0; i < template.size(); i++) {
                var in = template.get(i);
                var out = applyTemplate(node, in);
                ((ArrayNode) template).set(i, out);
            }
        } else if (template.isObject()) {
            if (template.isEmpty()) {
                return template;
            }

            var result = objectMapper.createObjectNode();
            template.fieldNames().forEachRemaining(key -> {
                var in = template.get(key);
                var out = applyTemplate(node, in);
                result.set(key, out);
            });
            return result;
        }

        return template;
    }

    private ObjectNode deepMerge(ObjectNode left, ObjectNode right) {
        try {
            return mergeMapper.updateValue(left, right);
        } catch (JsonProcessingException e) {
            throw new ViewProcessorException("Error while merging JSON objects: " + e.getMessage());
        }
    }

    public record RenderOverrides(boolean alwaysMerge) {

        public static RenderOverrides none() {
            return new RenderOverrides(false);
        }

        public static RenderOverrides merged() {
            return new RenderOverrides(true);
        }
    }
}
