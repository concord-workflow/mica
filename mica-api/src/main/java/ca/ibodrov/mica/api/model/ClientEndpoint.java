package ca.ibodrov.mica.api.model;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ClientEndpoint(@NotNull UUID id,
        @NotNull UUID clientId,
        @NotNull String clientName,
        @NotEmpty String uri,
        @NotEmpty String lastKnownStatus,
        @NotNull OffsetDateTime statusUpdatedAt) {
}
