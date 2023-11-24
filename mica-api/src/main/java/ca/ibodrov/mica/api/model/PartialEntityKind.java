package ca.ibodrov.mica.api.model;

import ca.ibodrov.mica.api.validation.ValidName;
import ca.ibodrov.mica.schema.ObjectSchemaNode;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Optional;

public record PartialEntityKind(@NotNull Optional<EntityKindId> id,
        @ValidName String name,
        @NotEmpty Optional<String> extendsKind,
        @NotNull Optional<OffsetDateTime> createdAt,
        @NotNull Optional<OffsetDateTime> updatedAt,
        @NotNull ObjectSchemaNode schema) {

    public static PartialEntityKind create(String name, ObjectSchemaNode schema) {
        return new PartialEntityKind(Optional.empty(), name, Optional.empty(), Optional.empty(), Optional.empty(),
                schema);
    }
}
