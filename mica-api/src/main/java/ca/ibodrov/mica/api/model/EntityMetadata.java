package ca.ibodrov.mica.api.model;

import java.time.OffsetDateTime;

public record EntityMetadata(EntityId id,
        String name,
        String kind,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public EntityVersion toVersion() {
        return new EntityVersion(id, updatedAt);
    }
}
