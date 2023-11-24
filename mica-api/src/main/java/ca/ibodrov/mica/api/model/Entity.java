package ca.ibodrov.mica.api.model;

import ca.ibodrov.mica.api.validation.ValidName;
import com.fasterxml.jackson.databind.JsonNode;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record Entity(@NotNull EntityId id,
        @ValidName String name,
        @NotEmpty String kind,
        @NotNull OffsetDateTime createdAt,
        @NotNull OffsetDateTime updatedAt,
        @NotNull JsonNode data) {
}
