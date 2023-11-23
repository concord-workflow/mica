package ca.ibodrov.mica.api.model;

import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record EntityKindVersion(@NotNull EntityKindId id, @NotNull OffsetDateTime updatedAt) {
}
