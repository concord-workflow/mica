package ca.ibodrov.mica.api.model;

import java.util.List;
import java.util.Optional;

public record BatchOperationResult(Optional<List<DeletedEntityVersionAndName>> deletedEntities) {
}
