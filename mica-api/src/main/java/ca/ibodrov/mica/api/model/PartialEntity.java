package ca.ibodrov.mica.api.model;

import ca.ibodrov.mica.api.validation.ValidName;
import com.fasterxml.jackson.databind.JsonNode;

import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Optional;

public record PartialEntity(@NotNull Optional<EntityId> id,
        @ValidName String name,
        @ValidName String kind,
        @NotNull Optional<OffsetDateTime> createdAt,
        @NotNull Optional<OffsetDateTime> updatedAt,
        @NotNull JsonNode data) implements WithMetadata {

    public static PartialEntity create(String name, String kind, JsonNode data) {
        return new PartialEntity(Optional.empty(), name, kind, Optional.empty(), Optional.empty(), data);
    }
}
