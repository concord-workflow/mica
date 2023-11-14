package ca.ibodrov.mica.api.model;

import ca.ibodrov.mica.schema.ObjectSchemaNode;

import java.time.OffsetDateTime;
import java.util.Optional;

public record Profile(Optional<ProfileId> id,
        String name,
        Optional<OffsetDateTime> createdAt,
        ObjectSchemaNode schema) {

    public static String KIND = "MicaProfile/v1";

    public static Profile of(String name, ObjectSchemaNode schema) {
        return new Profile(Optional.empty(), name, Optional.empty(), schema);
    }
}
