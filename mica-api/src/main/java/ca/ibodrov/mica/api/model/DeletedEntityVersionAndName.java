package ca.ibodrov.mica.api.model;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

public record DeletedEntityVersionAndName(EntityId id, Instant updatedAt, Instant deletedAt, String name) {

    public DeletedEntityVersionAndName {
        requireNonNull(id);
        requireNonNull(updatedAt);
        requireNonNull(deletedAt);
        requireNonNull(name);
    }
}
