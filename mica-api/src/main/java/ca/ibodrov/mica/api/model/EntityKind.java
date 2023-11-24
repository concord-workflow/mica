package ca.ibodrov.mica.api.model;

import ca.ibodrov.mica.api.validation.ValidName;
import ca.ibodrov.mica.schema.ObjectSchemaNode;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Optional;

public record EntityKind(@NotNull EntityKindId id,
        @ValidName String name,
        @NotNull Optional<String> extendsKind,
        @NotNull ObjectSchemaNode schema) {
}
