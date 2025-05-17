package ca.ibodrov.mica.api.model;

import ca.ibodrov.mica.api.validation.ValidName;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @implNote the changes in fields and annotations here must be synchronized
 *           with {@link PartialEntity}
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record Entity(@NotNull EntityId id,
        @ValidName String name,
        @ValidName String kind,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt,
        @NotNull Optional<Instant> deletedAt,
        @JsonProperty("__data") @JsonAnySetter @JsonAnyGetter @NotNull Map<String, JsonNode> data)
        implements EntityLike {

    public Entity {
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

    public EntityVersion version() {
        return new EntityVersion(id, updatedAt);
    }
}
