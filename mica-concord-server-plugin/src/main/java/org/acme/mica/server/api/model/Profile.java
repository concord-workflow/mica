package org.acme.mica.server.api.model;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record Profile(Optional<UUID> id,
        String name,
        Optional<OffsetDateTime> createdAt,
        Map<String, Object> schema) {

    public static String KIND = "MicaProfile/v1";
}
