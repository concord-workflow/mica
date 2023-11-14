package ca.ibodrov.mica.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING;
import static java.util.Objects.requireNonNull;

public record ProfileId(UUID id) {

    public static Optional<ProfileId> of(UUID id) {
        return Optional.of(id).map(ProfileId::new);
    }

    @JsonCreator(mode = DELEGATING)
    public ProfileId {
        requireNonNull(id);
    }

    @Override
    @JsonValue
    public UUID id() {
        return id;
    }
}
