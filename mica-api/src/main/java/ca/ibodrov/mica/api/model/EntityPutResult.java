package ca.ibodrov.mica.api.model;

import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record EntityPutResult(@NotNull EntityId entityId, @NotNull OffsetDateTime updatedAt) {
}
