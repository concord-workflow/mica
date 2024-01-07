package ca.ibodrov.mica.api.model;

import java.time.Instant;

public record EntityMetadata(EntityId id,
        String name,
        String kind,
        Instant createdAt,
        Instant updatedAt) {

    public EntityVersion toVersion() {
        return new EntityVersion(id, updatedAt);
    }
}
