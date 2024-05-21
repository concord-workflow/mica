package ca.ibodrov.mica.api.model;

import javax.validation.constraints.NotNull;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

public record EntityVersion(@NotNull EntityId id, @NotNull Instant updatedAt) {

    public EntityVersion {
        requireNonNull(id);
        requireNonNull(updatedAt);
    }
}
