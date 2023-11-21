package ca.ibodrov.mica.api.model;

import com.fasterxml.jackson.databind.JsonNode;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Optional;

public record Entity(Optional<EntityId> id,
        @NotEmpty String name,
        @NotEmpty String kind,
        Optional<OffsetDateTime> createdAt,
        Optional<OffsetDateTime> updatedAt,
        @NotNull JsonNode data) {
}
