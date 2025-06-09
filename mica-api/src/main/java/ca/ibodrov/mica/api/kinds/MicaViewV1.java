package ca.ibodrov.mica.api.kinds;

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
import ca.ibodrov.mica.api.model.ViewLike;
import ca.ibodrov.mica.api.validation.ValidName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * @implNote when adding or removing fields here, make sure to update
 *           {@link #toPartialEntity(ObjectMapper)}
 */
public record MicaViewV1(@ValidName String name,
        @NotNull Selector selector,
        @NotNull Data data,
        @NotNull Optional<Validation> validation,
        @NotNull Optional<JsonNode> parameters,
        @NotNull Optional<Caching> caching) implements ViewLike {

    public static final String MICA_VIEW_V1 = "/mica/view/v1";

    public MicaViewV1 {
        requireNonNull(name, "missing 'name'");
        requireNonNull(selector, "missing 'selector'");
        requireNonNull(data, "missing 'data'");
        requireNonNull(validation, "'validation' cannot be null");
        requireNonNull(parameters, "'parameters' cannot be null");
    }

    public record Selector(Optional<List<String>> includes,
            @ValidName String entityKind,
            @NotNull Optional<List<String>> namePatterns) implements ViewLike.Selector {

        public static Selector byEntityKind(String entityKind) {
            return new Selector(Optional.empty(), entityKind, Optional.empty());
        }

        public Selector withNamePatterns(List<String> namePatterns) {
            return new Selector(this.includes, this.entityKind, Optional.of(namePatterns));
        }

        public Selector withIncludes(List<String> includes) {
            return new Selector(Optional.of(includes), this.entityKind, this.namePatterns);
        }
    }

    public record Data(JsonNode jsonPath,
            Optional<JsonNode> jsonPatch,
            Optional<Boolean> flatten,
            Optional<Boolean> merge,
            Optional<JsonNode> mergeBy,
            Optional<List<String>> dropProperties,
            Optional<Map<String, JsonNode>> map,
            Optional<JsonNode> template) implements ViewLike.Data {

        public static Data jsonPath(String jsonPath) {
            return new Data(TextNode.valueOf(jsonPath), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        public static Data jsonPaths(ObjectMapper objectMapper, String... jsonPaths) {
            var jsonPath = objectMapper.convertValue(requireNonNull(jsonPaths), ArrayNode.class);
            return new Data(jsonPath, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(),
                    Optional.empty(), Optional.empty());
        }

        public Data withMerge() {
            return new Data(this.jsonPath, this.jsonPatch, this.flatten, Optional.of(true), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty());
        }

        public Data withMergeBy(String mergeBy) {
            return new Data(this.jsonPath, this.jsonPatch, this.flatten, this.merge,
                    Optional.of(TextNode.valueOf(mergeBy)),
                    Optional.empty(), Optional.empty(), Optional.empty());

        }

        public Data withDropProperties(List<String> dropProperties) {
            return new Data(this.jsonPath, this.jsonPatch, this.flatten, this.merge, Optional.empty(),
                    Optional.of(dropProperties),
                    Optional.empty(), Optional.empty());
        }
    }

    // TODO add @NotNull
    public record Validation(String asEntityKind) implements ViewLike.Validation {

        public static Validation asEntityKind(String asEntityKind) {
            return new Validation(asEntityKind);
        }
    }

    public record Caching(@NotNull Optional<String> enabled,
            @NotNull Optional<String> ttl) implements ViewLike.Caching {
    }

    public PartialEntity toPartialEntity(ObjectMapper objectMapper) {
        var props = new HashMap<String, JsonNode>();
        this.parameters.ifPresent(params -> props.put("parameters",
                objectMapper.convertValue(params, JsonNode.class)));
        props.put("selector", objectMapper.convertValue(this.selector, JsonNode.class));
        props.put("data", objectMapper.convertValue(this.data, JsonNode.class));
        props.put("validation", objectMapper.convertValue(this.validation, JsonNode.class));
        return PartialEntity.create(this.name, MICA_VIEW_V1, props);
    }

    public static class Builder {

        private String name;
        private Selector selector;
        private Data data;
        private Validation validation;
        private JsonNode parameters;
        private Caching caching;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder selector(Selector selector) {
            this.selector = selector;
            return this;
        }

        public Builder data(Data data) {
            this.data = data;
            return this;
        }

        public Builder validation(Validation validation) {
            this.validation = validation;
            return this;
        }

        public Builder parameters(JsonNode parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder caching(Caching caching) {
            this.caching = caching;
            return this;
        }

        public MicaViewV1 build() {
            return new MicaViewV1(
                    name,
                    selector,
                    data,
                    Optional.ofNullable(validation),
                    Optional.ofNullable(parameters),
                    Optional.ofNullable(caching));
        }
    }
}
