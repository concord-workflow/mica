package ca.ibodrov.mica.api.model;

import javax.validation.constraints.NotNull;
import java.time.Instant;

public record EntityVersion(@NotNull EntityId id, @NotNull Instant updatedAt) {
}
