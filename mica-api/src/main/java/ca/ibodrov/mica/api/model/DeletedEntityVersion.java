package ca.ibodrov.mica.api.model;

import javax.validation.constraints.NotNull;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

public record DeletedEntityVersion(@NotNull EntityId id, @NotNull Instant updatedAt, @NotNull Instant deletedAt) {

    public DeletedEntityVersion {
        requireNonNull(id);
        requireNonNull(updatedAt);
        requireNonNull(deletedAt);
    }

    public EntityVersion asVersion() {
        return new EntityVersion(this.id(), this.updatedAt());
    }
}
