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
import ca.ibodrov.mica.api.validation.ValidName;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.validation.constraints.NotNull;
import java.util.HashMap;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT;
import static java.util.Objects.requireNonNull;

@JsonInclude(NON_ABSENT)
public record MicaKindV1(@ValidName String name,
        @NotNull JsonNode schema) {

    public static final String MICA_KIND_V1 = "/mica/kind/v1";
    public static final String SCHEMA_PROPERTY = "schema";

    public MicaKindV1 {
        requireNonNull(name, "missing 'name'");
        requireNonNull(schema, "missing 'schema'");
    }

    public PartialEntity toPartialEntity(ObjectMapper objectMapper) {
        var props = new HashMap<String, JsonNode>();
        props.put(SCHEMA_PROPERTY, objectMapper.convertValue(schema, JsonNode.class));
        return PartialEntity.create(this.name, MICA_KIND_V1, props);
    }

    public static class Builder {

        private String name;
        private JsonNode schema;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder schema(JsonNode schema) {
            this.schema = schema;
            return this;
        }

        public MicaKindV1 build() {
            return new MicaKindV1(name, schema);
        }
    }
}
