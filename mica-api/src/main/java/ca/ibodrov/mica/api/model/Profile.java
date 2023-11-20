package ca.ibodrov.mica.api.model;

import ca.ibodrov.mica.schema.ObjectSchemaNode;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Optional;

public record Profile(Optional<ProfileId> id,
        @NotEmpty String name,
        Optional<OffsetDateTime> createdAt,
        @NotNull ObjectSchemaNode schema) {

    public static String KIND = "MicaProfile/v1";

    public static Profile of(String name, ObjectSchemaNode schema) {
        return new Profile(Optional.empty(), name, Optional.empty(), schema);
    }
}
