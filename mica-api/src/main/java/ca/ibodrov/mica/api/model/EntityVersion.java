package ca.ibodrov.mica.api.model;

import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record EntityVersion(@NotNull EntityId id, @NotNull OffsetDateTime updatedAt) {
}
