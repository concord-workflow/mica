package ca.ibodrov.mica.api.model;

import java.time.Instant;

public record EntityVersionAndName(EntityId id, Instant updatedAt, String name) {
}
