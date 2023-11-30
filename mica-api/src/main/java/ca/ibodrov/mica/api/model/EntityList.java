package ca.ibodrov.mica.api.model;

import java.util.List;

public record EntityList(int limit, List<EntityMetadata> data) {
}
