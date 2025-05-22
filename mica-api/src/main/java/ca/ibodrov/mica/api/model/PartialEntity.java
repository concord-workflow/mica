package ca.ibodrov.mica.api.model;

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

import ca.ibodrov.mica.api.validation.ValidName;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @implNote the changes in fields and annotations here must be synchronized
 *           with {@link Entity}
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record PartialEntity(@NotNull Optional<EntityId> id,
        @ValidName String name,
        @ValidName String kind,
        @NotNull Optional<Instant> createdAt,
        @NotNull Optional<Instant> updatedAt,
        @NotNull Optional<Instant> deletedAt,
        @JsonProperty("__data") @JsonAnySetter @JsonAnyGetter @NotNull Map<String, JsonNode> data)
        implements EntityLike {

    public static PartialEntity create(String name, String kind, Map<String, JsonNode> data) {
        return new PartialEntity(Optional.empty(), name, kind, Optional.empty(), Optional.empty(), Optional.empty(),
                data);
    }

    public PartialEntity {
        data = new LinkedHashMap<>(data != null ? data : Map.of()); // has to be mutable to support @JsonAnySetter
    }

    @JsonAnyGetter
    public JsonNode getProperty(String name) {
        return data.get(name);
    }

    @JsonAnySetter
    public void setProperty(String name, JsonNode value) {
        data.put(name, value);
    }

    public PartialEntity withVersion(EntityVersion version) {
        return new PartialEntity(Optional.of(version.id()), name(), kind(), createdAt(),
                Optional.of(version.updatedAt()), deletedAt(), data());
    }

    public PartialEntity withName(String name) {
        return new PartialEntity(id, name, kind, createdAt, updatedAt, deletedAt, data);
    }

    public PartialEntity withKind(String kind) {
        return new PartialEntity(id, name, kind, createdAt, updatedAt, deletedAt, data);
    }

    public Optional<EntityVersion> version() {
        return id.flatMap(i -> updatedAt.map(u -> new EntityVersion(i, u)));
    }

    public PartialEntity withoutUpdatedAt() {
        return new PartialEntity(id, name, kind, createdAt, Optional.empty(), deletedAt, data);
    }
}
